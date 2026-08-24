package com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.dataobject.McpServerDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP Server Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface McpServerMapper extends BaseMapperX<McpServerDO> {

    /**
     * 分页查询（tenant_id 由租户插件自动过滤）
     *
     * @param name      名称（模糊匹配），可空
     * @param transport 传输类型编码（精确），可空
     * @param enabled   是否启用，可空
     */
    default PageResult<McpServerDO> selectPage(PageParam pageParam, String name, String transport,
                                               Boolean enabled) {
        return selectPage(pageParam, new LambdaQueryWrapperX<McpServerDO>()
                .likeIfPresent(McpServerDO::getName, name)
                .eqIfPresent(McpServerDO::getTransport, transport)
                .eqIfPresent(McpServerDO::getEnabled, enabled)
                .orderByDesc(McpServerDO::getId));
    }

}
