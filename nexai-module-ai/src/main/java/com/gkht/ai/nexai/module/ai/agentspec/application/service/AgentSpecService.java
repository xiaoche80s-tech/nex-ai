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
     * @param userId 当前登录用户编号（用户级归属的创建者），可空
     * @return 规格编号
     */
    Long createSpec(AgentSpecCreateCommand command, Long userId);

    /**
     * 编辑规格：更新主体信息并覆盖草稿；无草稿时即「再编辑生成新草稿」。
     * spec_code 与归属层级不可变（编辑面不含）；用户级规格仅归属用户可编辑。
     *
     * @param userId 当前登录用户编号，可空
     */
    void updateSpec(AgentSpecUpdateCommand command, Long userId);

    /**
     * 删除规格及其全部版本
     *
     * @param userId 当前登录用户编号，可空
     */
    void deleteSpec(Long id, Long userId);

    /**
     * 发布当前草稿为不可变新版本，默认版本指针前移
     *
     * @param userId 当前登录用户编号，可空
     * @return 新版本号
     */
    Integer publishSpec(AgentSpecPublishCommand command, Long userId);

    /**
     * 切换当前默认版本（回滚 / 迭代入口）
     *
     * @param userId 当前登录用户编号，可空
     */
    void switchDefaultVersion(AgentSpecSwitchVersionCommand command, Long userId);

    /**
     * 规格分页查询（可见性按归属层级：租户级租户内全见、用户级仅归属用户）
     *
     * @param userId 当前登录用户编号，可空
     */
    PageResult<AgentSpecDTO> getSpecPage(AgentSpecPageQuery query, Long userId);

    /**
     * 规格详情：含草稿与当前默认版本快照
     */
    AgentSpecDetailDTO getSpec(Long id);

    /**
     * 版本历史（按版本号倒序）
     */
    List<AgentSpecVersionDTO> getVersionList(Long specId);

}
