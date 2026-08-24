package com.gkht.ai.nexai.module.ai.agentspec.domain.repository;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;

import java.util.List;

/**
 * 智能体规格聚合仓储端口（按聚合不按表）：规格主体（草稿/当前版本指针）与
 * 其下的不可变版本快照同属一个聚合边界，事务内一起持久化。
 *
 * <p>MVP 仅创建路径（save = insert）；主体字段更新与按编号读取随版本编辑/发布工单扩展；
 * 列表查询走轻量读写分离（应用服务经 Mapper 直查转 DTO），不经本端口。</p>
 */
public interface AgentSpecRepository {

    /**
     * 保存聚合：无编号时插入（回填编号）
     *
     * @return 聚合编号
     */
    Long save(AgentSpec spec);

    /**
     * 按编号读取聚合（含草稿与当前版本指针；版本快照列表按需另查）
     */
    AgentSpec findById(Long id);

    /**
     * 按业务编码读取聚合（租户内寻址——OpenAI 兼容出口的 model 路由用，工单 16）；
     * 同租户内编码唯一（部分唯一索引保证），不存在返回 null
     */
    AgentSpec findBySpecCode(String specCode);

    /**
     * 读取规格下的版本号最大值，从未发布返回 null（发布时事务内计算新版本号）
     */
    Integer findMaxVersionNo(Long specId);

    /**
     * 保存版本快照（不可变，仅插入；specId 未落库时先回填规格编号）
     */
    Long saveVersion(AgentSpecVersion version);

    /**
     * 读取规格下的全部版本快照（按版本号升序），未发布返回空列表
     */
    List<AgentSpecVersion> listVersions(Long specId);

    /**
     * 规格下是否存在某版本号（切换当前版本前的存在性校验）
     */
    boolean existsVersion(Long specId, int versionNo);

    /**
     * 更新聚合主体（草稿与当前版本指针落库）
     */
    void update(AgentSpec spec);

}
