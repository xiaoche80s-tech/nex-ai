package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.EffectiveSpecSnapshot;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;

import java.util.List;

/**
 * 智能体规格应用服务：创建（携带首个草稿）、分页查询、发布不可变版本快照、
 * 版本列表与切换当前版本。
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
     * 更新规格（编辑面）：整体替换主体元数据与草稿，只动草稿不触碰已发布快照。
     *
     * @param command 更新命令
     */
    void updateSpec(AgentSpecUpdateCommand command);

    /**
     * 查询规格详情（编辑面回填用，含全量草稿配置平铺）
     *
     * @param id 规格编号
     */
    AgentSpecDetailDTO getSpec(Long id);

    /**
     * 分页查询规格（轻量读写分离：经 Mapper 直查转 DTO，用户级仅归属用户可见）
     *
     * @param query  分页查询
     * @param userId 当前登录用户编号（用户级可见性过滤），可空
     */
    PageResult<AgentSpecDTO> getSpecPage(AgentSpecPageQuery query, Long userId);

    /**
     * 发布当前草稿：固化为不可变版本快照（全量四层配置）并推进当前版本指针。
     * 版本号 = 现有最大 + 1（首次发布 = 1），事务内计算并以 DB 唯一索引兜底并发。
     *
     * @param command 发布命令
     * @return 新版本号
     */
    Integer publishSpec(AgentSpecPublishCommand command);

    /**
     * 查询规格的版本列表（按版本号升序，含当前版本标识）
     *
     * @param specId 规格编号
     */
    List<AgentSpecVersionDTO> listSpecVersions(Long specId);

    /**
     * 切换当前生效版本（回退当前版本指针；快照本身不可变）
     *
     * @param command 切换命令
     */
    void switchSpecVersion(AgentSpecSwitchVersionCommand command);

    /**
     * 解析生效快照（规格 + 当前版本指针指向的版本快照），运行侧入口的统一寻址方式
     *
     * @throws com.gkht.ai.nexai.framework.common.exception.ServiceException 规格不存在、
     *         尚未发布版本或当前版本快照缺失
     */
    EffectiveSpecSnapshot resolveCurrentVersion(Long specId);

    /**
     * 按业务编码解析生效快照（OpenAI 兼容出口的 model 路由寻址方式）
     *
     * @throws com.gkht.ai.nexai.framework.common.exception.ServiceException 规格不存在、
     *         尚未发布版本或当前版本快照缺失
     */
    EffectiveSpecSnapshot resolveCurrentVersionByCode(String specCode);

}
