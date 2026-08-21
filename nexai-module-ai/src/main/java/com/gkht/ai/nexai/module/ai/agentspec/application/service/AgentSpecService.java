package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;

import java.util.List;

/**
 * 智能体规格应用服务：草稿编辑 / 发布 / 版本历史 / 默认版本切换的用例编排。
 */
public interface AgentSpecService {

    /**
     * 创建规格（携带首个草稿）
     *
     * @return 规格编号
     */
    Long createSpec(AgentSpecCreateCommand command);

    /**
     * 编辑规格：更新主体信息并覆盖草稿；无草稿时即「再编辑生成新草稿」
     */
    void updateSpec(AgentSpecUpdateCommand command);

    /**
     * 删除规格及其全部版本
     */
    void deleteSpec(Long id);

    /**
     * 发布当前草稿为不可变新版本，默认版本指针前移
     *
     * @return 新版本号
     */
    Integer publishSpec(AgentSpecPublishCommand command);

    /**
     * 切换当前默认版本（回滚 / 迭代入口）
     */
    void switchDefaultVersion(AgentSpecSwitchVersionCommand command);

    /**
     * 规格分页查询
     */
    PageResult<AgentSpecDTO> getSpecPage(AgentSpecPageQuery query);

    /**
     * 规格详情：含草稿与当前默认版本快照
     */
    AgentSpecDetailDTO getSpec(Long id);

    /**
     * 版本历史（按版本号倒序）
     */
    List<AgentSpecVersionDTO> getVersionList(Long specId);

}
