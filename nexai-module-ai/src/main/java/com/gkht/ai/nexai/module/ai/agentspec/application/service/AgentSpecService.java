package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;

/**
 * 智能体规格应用服务：创建（携带首个草稿）与分页查询。
 */
public interface AgentSpecService {

    /**
     * 创建规格（携带首个草稿）
     *
     * @param command 创建命令
     * @param userId  当前登录用户编号（用户级规格的归属用户来源），无登录态为 null
     * @return 规格编号
     */
    Long createSpec(AgentSpecCreateCommand command, Long userId);

    /**
     * 分页查询规格（轻量读写分离：经 Mapper 直查转 DTO，用户级仅归属用户可见）
     *
     * @param query  分页查询
     * @param userId 当前登录用户编号（用户级可见性过滤），可空
     */
    PageResult<AgentSpecDTO> getSpecPage(AgentSpecPageQuery query, Long userId);

}
