package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.repository.McpServerRepository;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpOwnerType;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.converter.McpServerConverter;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.dataobject.McpServerDO;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.mapper.McpServerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * MCP Server Repository 实现：DO ↔ 领域模型适配，聚合重建经 reconstitute。
 * headers 列走 EncryptTypeHandler（AES 密文），null 列被 updateById 忽略与凭证保留语义一致。
 */
@Repository
public class McpServerRepositoryImpl implements McpServerRepository {

    @Resource
    private McpServerMapper mcpServerMapper;

    @Resource
    private McpServerConverter mcpServerConverter;

    @Override
    public Long save(McpServer server) {
        McpServerDO dataObject = mcpServerConverter.toDataObject(server);
        if (dataObject.getId() == null) {
            mcpServerMapper.insert(dataObject);
            server.assignId(dataObject.getId());
        } else {
            mcpServerMapper.updateById(dataObject);
        }
        return dataObject.getId();
    }

    @Override
    public McpServer findById(Long id) {
        McpServerDO dataObject = mcpServerMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public void deleteById(Long id) {
        mcpServerMapper.deleteById(id);
    }

    private McpServer reconstitute(McpServerDO dataObject) {
        McpTransport transport = parseEnum(McpTransport.class, dataObject.getTransport());
        McpOwnerType ownerType = parseEnum(McpOwnerType.class, dataObject.getOwnerType());
        if (transport == null || ownerType == null) {
            // 编码非法属于脏数据，直接失败暴露而非静默吞掉
            throw new IllegalStateException(String.format(
                    "MCP Server #%s 的编码非法：transport=%s, ownerType=%s",
                    dataObject.getId(), dataObject.getTransport(), dataObject.getOwnerType()));
        }
        return McpServer.reconstitute(dataObject.getId(), dataObject.getName(), transport,
                dataObject.getEndpoint(), dataObject.getCommand(),
                mcpServerConverter.jsonToStringList(dataObject.getArgs()),
                mcpServerConverter.jsonToStringMap(dataObject.getEnv()),
                mcpServerConverter.jsonToStringMap(dataObject.getHeaders()),
                dataObject.getTimeoutSeconds(),
                mcpServerConverter.jsonToStringList(dataObject.getAllowedTools()),
                mcpServerConverter.jsonToStringList(dataObject.getAvailableTools()),
                Boolean.TRUE.equals(dataObject.getEnabled()), ownerType,
                dataObject.getCreateTime(), dataObject.getUpdateTime());
    }

    /** 枚举编码安全解析：null/非法编码归 null（由调用方按脏数据处理） */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String code) {
        try {
            return code == null ? null : Enum.valueOf(type, code);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

}
