package com.gkht.ai.nexai.module.ai.mcpserver.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerCreateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpProbeResultDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpServerDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.query.McpServerPageQuery;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.exception.McpToolsWhitelistInvalidException;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.gateway.McpServerGateway;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.repository.McpServerRepository;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpOwnerType;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpProbeResult;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.converter.McpServerConverter;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.mapper.McpServerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.stream.Collectors;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MCP_SERVER_CONFIG_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MCP_SERVER_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MCP_SERVER_TOOLS_WHITELIST_INVALID;

/**
 * MCP Server 应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查转 DTO；
 * 探测经 {@link McpServerGateway} 端口（agentscope 三传输直用），成功时回写工具清单缓存。
 */
@Service
@Validated
public class McpServerServiceImpl implements McpServerService {

    /** 归属维度缺省值（双归属字段先行，MVP 固定 TENANT = BYO-MCP） */
    private static final McpOwnerType DEFAULT_OWNER_TYPE = McpOwnerType.TENANT;

    @Resource
    private McpServerRepository mcpServerRepository;

    @Resource
    private McpServerMapper mcpServerMapper;

    @Resource
    private McpServerConverter mcpServerConverter;

    @Resource
    private McpServerGateway mcpServerGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMcpServer(McpServerCreateCommand command) {
        McpServer server;
        try {
            server = McpServer.create(command.getName(), McpTransport.valueOf(command.getTransport()),
                    command.getEndpoint(), command.getCommand(), command.getArgs(), command.getEnv(),
                    command.getHeaders(), command.getTimeoutSeconds(), command.getAllowedTools(),
                    DEFAULT_OWNER_TYPE);
        } catch (IllegalArgumentException ex) {
            throw exception(MCP_SERVER_CONFIG_INVALID, ex.getMessage());
        }
        return mcpServerRepository.save(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMcpServer(McpServerUpdateCommand command) {
        McpServer server = requireMcpServer(command.getId());
        try {
            server.update(command.getName(), McpTransport.valueOf(command.getTransport()),
                    command.getEndpoint(), command.getCommand(), command.getArgs(), command.getEnv(),
                    command.getHeaders(), command.getTimeoutSeconds(), command.getAllowedTools());
        } catch (McpToolsWhitelistInvalidException ex) {
            // 专项错误码按异常类型分派（不依赖消息文本，改提示文案不影响路由）
            throw exception(MCP_SERVER_TOOLS_WHITELIST_INVALID, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw exception(MCP_SERVER_CONFIG_INVALID, ex.getMessage());
        }
        mcpServerRepository.save(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMcpServerStatus(McpServerUpdateStatusCommand command) {
        McpServer server = requireMcpServer(command.getId());
        if (Boolean.TRUE.equals(command.getEnabled())) {
            server.enable();
        } else {
            server.disable();
        }
        mcpServerRepository.save(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMcpServer(Long id) {
        requireMcpServer(id);
        mcpServerRepository.deleteById(id);
    }

    @Override
    public McpServerDTO getMcpServer(Long id) {
        return mcpServerConverter.toDTO(requireMcpServer(id));
    }

    @Override
    public PageResult<McpServerDTO> getMcpServerPage(McpServerPageQuery query) {
        return mcpServerConverter.toDTOPageFromDO(mcpServerMapper.selectPage(query, query.getName(),
                query.getTransport(), query.getEnabled()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpProbeResultDTO probeMcpServer(Long id) {
        McpServer server = requireMcpServer(id);
        McpProbeResult result = mcpServerGateway.probe(server, TenantContextHolder.getTenantId());
        if (result.isSuccess()) {
            // 成功才回写缓存：失败保留上次清单（挂载编辑面候选项不至于被一次抖动清空）
            server.applyProbeResult(result.getTools().stream()
                    .map(tool -> tool.getName()).collect(Collectors.toList()));
            mcpServerRepository.save(server);
        }
        McpProbeResultDTO dto = new McpProbeResultDTO();
        dto.setSuccess(result.isSuccess());
        dto.setElapsedMs(result.getElapsedMs());
        dto.setMessage(result.getMessage());
        dto.setTools(result.getTools().stream().map(tool -> {
            McpProbeResultDTO.McpToolDTO toolDto = new McpProbeResultDTO.McpToolDTO();
            toolDto.setName(tool.getName());
            toolDto.setDescription(tool.getDescription());
            return toolDto;
        }).collect(Collectors.toList()));
        return dto;
    }

    private McpServer requireMcpServer(Long id) {
        McpServer server = mcpServerRepository.findById(id);
        if (server == null) {
            throw exception(MCP_SERVER_NOT_EXISTS);
        }
        return server;
    }

}
