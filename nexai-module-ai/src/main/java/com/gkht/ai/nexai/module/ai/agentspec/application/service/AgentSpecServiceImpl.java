package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.McpServerMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.McpServerMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverter;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CODE_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CONFIG_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED;

/**
 * 智能体规格应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查转 DTO。
 *
 * <p>spec_code 唯一性按归属层级：应用层预校验（{@link AgentSpecMapper#existsBySpecCode}）
 * + 生产库部分唯一索引兜底并发（测试库无索引，行为由预校验保证）。</p>
 */
@Service
@Validated
public class AgentSpecServiceImpl implements AgentSpecService {

    /** 归属层级缺省值 */
    private static final OwnerLevel DEFAULT_OWNER_LEVEL = OwnerLevel.TENANT;

    @Resource
    private AgentSpecRepository agentSpecRepository;

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private AgentSpecConverter agentSpecConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpec(AgentSpecCreateCommand command, Long userId) {
        // MVP 开放范围：TENANT/USER（命令层 @Pattern 之外，服务层直调场景同样拦截）
        OwnerLevel ownerLevel;
        try {
            ownerLevel = command.getOwnerLevel() == null
                    ? DEFAULT_OWNER_LEVEL : OwnerLevel.valueOf(command.getOwnerLevel());
        } catch (IllegalArgumentException ex) {
            throw exception(AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED);
        }
        if (ownerLevel == OwnerLevel.PLATFORM) {
            throw exception(AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED);
        }
        // 用户级归属 = 创建者（归属用户由服务端从登录态填充，不信任前端传入）
        Long ownerUserId = ownerLevel == OwnerLevel.USER ? userId : null;
        if (ownerLevel == OwnerLevel.USER && ownerUserId == null) {
            throw exception(AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED);
        }
        if (agentSpecMapper.existsBySpecCode(ownerLevel, ownerUserId, command.getSpecCode())) {
            throw exception(AGENT_SPEC_CODE_DUPLICATE, command.getSpecCode());
        }
        AgentSpec spec;
        try {
            spec = AgentSpec.create(command.getName(), command.getSpecCode(), command.getIcon(),
                    ownerLevel, ownerUserId, toConfig(command));
        } catch (IllegalArgumentException ex) {
            // 领域校验（执行环境链、文本边界等跨字段规则）转业务异常渲染
            throw exception(AGENT_SPEC_CONFIG_INVALID, ex.getMessage());
        }
        try {
            return agentSpecRepository.save(spec);
        } catch (DuplicateKeyException ex) {
            // 预校验与插入之间的并发窗口由生产库部分唯一索引兜底，转业务错误码而非 500
            throw exception(AGENT_SPEC_CODE_DUPLICATE, command.getSpecCode());
        }
    }

    @Override
    public PageResult<AgentSpecDTO> getSpecPage(AgentSpecPageQuery query, Long userId) {
        return agentSpecConverter.toDTOPage(
                agentSpecMapper.selectPage(query, query.getName(), query.getSpecCode(), userId));
    }

    /** 平铺命令 → 四层配置值对象（模型引用/自描述草稿态可空，发布时校验补齐） */
    private AgentSpecConfig toConfig(AgentSpecCreateCommand command) {
        GenerateOptions generateOptions = command.getTemperature() == null && command.getTopP() == null
                && command.getMaxTokens() == null ? null
                : GenerateOptions.of(command.getTemperature(), command.getTopP(), command.getMaxTokens());
        List<McpServerMount> mcpServers = command.getMcpServers() == null ? null
                : command.getMcpServers().stream().map(this::toMount).toList();
        ExecutionEnvConfig executionEnv = Boolean.TRUE.equals(command.getWorkspaceEnabled())
                || Boolean.TRUE.equals(command.getSandboxEnabled())
                || command.getCapabilities() != null && !command.getCapabilities().isEmpty()
                ? ExecutionEnvConfig.of(Boolean.TRUE.equals(command.getWorkspaceEnabled()),
                        Boolean.TRUE.equals(command.getSandboxEnabled()),
                        command.getCapabilities() == null ? null
                                : command.getCapabilities().stream().map(ExecutionCapability::valueOf).toList())
                : null;
        return AgentSpecConfig.of(command.getModelId(), command.getDescription(), command.getSystemPrompt(),
                command.getMaxIters(), generateOptions, command.getSkillIds(), mcpServers, executionEnv);
    }

    private McpServerMount toMount(McpServerMountCommand mountCommand) {
        return McpServerMount.of(mountCommand.getServerId(), mountCommand.getAllowedTools());
    }

}
