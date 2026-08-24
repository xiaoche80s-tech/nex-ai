package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecDraftFields;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.ToolMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.EffectiveSpecSnapshot;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderFile;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.FolderType;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource;
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
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_PUBLISH_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_CONFLICT;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASSEMBLE_INVALID;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpec(AgentSpecUpdateCommand command) {
        AgentSpec spec = requireSpec(command.getId());
        try {
            // 编辑面：只动主体元数据与草稿，不触碰已发布快照（replaceDraft 领域语义保证）
            spec.updateProfile(command.getName(), command.getIcon());
            spec.replaceDraft(toConfig(command));
        } catch (IllegalArgumentException ex) {
            // 领域校验（执行环境链、文本边界等跨字段规则）转业务异常渲染
            throw exception(AGENT_SPEC_CONFIG_INVALID, ex.getMessage());
        }
        agentSpecRepository.save(spec);
    }

    @Override
    public AgentSpecDetailDTO getSpec(Long id) {
        return agentSpecConverter.toDetailDTO(requireSpec(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer publishSpec(AgentSpecPublishCommand command) {
        AgentSpec spec = requireSpec(command.getId());
        try {
            // 版本簿记（max+1、快照插入、指针推进）收拢在端口单方法内，事务内原子
            return agentSpecRepository.persistPublication(spec, command.getNote());
        } catch (IllegalStateException ex) {
            throw exception(AGENT_SPEC_PUBLISH_INVALID, ex.getMessage());
        } catch (DuplicateKeyException ex) {
            // 并发发布由版本唯一索引兜底（版本号重复），转业务错误码而非 500
            throw exception(AGENT_SPEC_VERSION_CONFLICT);
        }
    }

    @Override
    public List<AgentSpecVersionDTO> listSpecVersions(Long specId) {
        AgentSpec spec = requireSpec(specId);
        List<AgentSpecVersionDTO> versions = agentSpecRepository.listVersions(specId).stream()
                .map(agentSpecConverter::toVersionDTO).toList();
        // 当前生效版本标识按版本指针判等（当前版本指针指向哪个版本号，哪个版本即 current；
        // 未发布规格全部非 current——恒为非 null，前端直接判断）
        Integer currentVersionNo = spec.getCurrentVersionNo();
        versions.forEach(version ->
                version.setCurrent(currentVersionNo != null && currentVersionNo.equals(version.getVersionNo())));
        return versions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchSpecVersion(AgentSpecSwitchVersionCommand command) {
        AgentSpec spec = requireSpec(command.getId());
        // 解析出目标版本对象即证明其存在（存在性不再以布尔旗标喂给聚合）
        AgentSpecVersion target = agentSpecRepository.listVersions(command.getId()).stream()
                .filter(v -> v.getVersionNo().equals(command.getVersionNo()))
                .findFirst()
                .orElseThrow(() -> exception(AGENT_SPEC_VERSION_NOT_EXISTS, command.getId()));
        try {
            spec.switchToVersion(target);
        } catch (IllegalStateException ex) {
            throw exception(AGENT_SPEC_VERSION_NOT_EXISTS, command.getId());
        }
        agentSpecRepository.save(spec);
    }

    @Override
    public EffectiveSpecSnapshot resolveCurrentVersion(Long specId) {
        return resolveCurrent(requireSpec(specId));
    }

    @Override
    public EffectiveSpecSnapshot resolveCurrentVersionByCode(String specCode) {
        AgentSpec spec = agentSpecRepository.findBySpecCode(specCode);
        if (spec == null) {
            throw exception(AGENT_SPEC_NOT_EXISTS);
        }
        return resolveCurrent(spec);
    }

    /**
     * 生效快照解析（单一真相）：当前版本指针指向的版本快照，缺失即数据一致性问题，
     * 显式报错（错误码/消息与原两处入口内联实现逐字一致）。
     */
    private EffectiveSpecSnapshot resolveCurrent(AgentSpec spec) {
        if (!spec.hasPublishedVersion()) {
            throw exception(SESSION_ASSEMBLE_INVALID, "规格尚未发布版本");
        }
        AgentSpecVersion version = agentSpecRepository.listVersions(spec.getId()).stream()
                .filter(v -> v.getVersionNo() == spec.getCurrentVersionNo())
                .findFirst()
                .orElseThrow(() -> exception(SESSION_ASSEMBLE_INVALID, "当前版本快照缺失"));
        return new EffectiveSpecSnapshot(spec, version);
    }

    /** 读取规格，不存在报业务异常（不存在/跨租户/已删除均归此） */
    private AgentSpec requireSpec(Long id) {
        AgentSpec spec = agentSpecRepository.findById(id);
        if (spec == null) {
            throw exception(AGENT_SPEC_NOT_EXISTS);
        }
        return spec;
    }

    /** 平铺命令 → 四层配置值对象（模型引用/自描述草稿态可空，发布时校验补齐） */
    private AgentSpecConfig toConfig(AgentSpecDraftFields command) {
        GenerateOptions generateOptions = command.getTemperature() == null && command.getTopP() == null
                && command.getMaxTokens() == null ? null
                : GenerateOptions.of(command.getTemperature(), command.getTopP(), command.getMaxTokens());
        List<ToolMount> tools = command.getTools() == null ? null
                : command.getTools().stream().map(this::toMount).toList();
        List<FolderMount> folders = command.getFolders() == null ? null
                : command.getFolders().stream().map(this::toFolderMount).toList();
        ExecutionEnvConfig executionEnv = Boolean.TRUE.equals(command.getWorkspaceEnabled())
                || Boolean.TRUE.equals(command.getSandboxEnabled())
                || command.getCapabilities() != null && !command.getCapabilities().isEmpty()
                ? ExecutionEnvConfig.of(Boolean.TRUE.equals(command.getWorkspaceEnabled()),
                        Boolean.TRUE.equals(command.getSandboxEnabled()),
                        command.getCapabilities() == null ? null
                                : command.getCapabilities().stream().map(ExecutionCapability::valueOf).toList())
                : null;
        return AgentSpecConfig.of(command.getModelId(), command.getDescription(), command.getSystemPrompt(),
                command.getMaxIters(), generateOptions, command.getSkillIds(), tools, folders, executionEnv);
    }

    private ToolMount toMount(ToolMountCommand mountCommand) {
        return ToolMount.of(ToolSource.valueOf(mountCommand.getSource()),
                mountCommand.getSourceId(), mountCommand.getAllowedTools(),
                mountCommand.getSensitiveTools());
    }

    /** 文件夹挂载命令 → 值对象（文件清单为上传接口返回的凭证：url + 哈希 + 字节数） */
    private FolderMount toFolderMount(FolderMountCommand folderCommand) {
        return FolderMount.of(FolderType.valueOf(folderCommand.getType()), folderCommand.getName(),
                folderCommand.getFiles() == null ? null : folderCommand.getFiles().stream()
                        .map(file -> FolderFile.of(file.getPath(), file.getUrl(),
                                file.getContentHash(), file.getSize() == null ? 0L : file.getSize()))
                        .toList());
    }

}
