package com.gkht.ai.nexai.module.ai.agentspec.domain.repository;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;

import java.util.List;

/**
 * 智能体规格聚合 Repository 端口。规格与版本同属一个聚合，同一事务边界内保持一致。
 *
 * <p>版本保存刻意命名为 {@link #createVersion}（insert-only）且不提供任何更新方法——
 * 已发布版本不可变是端口契约而非实现细节。</p>
 */
public interface AgentSpecRepository {

    /**
     * 保存规格主体（新增或更新），返回规格编号
     */
    Long save(AgentSpec spec);

    /**
     * 按编号查找规格聚合，不存在返回 null
     */
    AgentSpec findById(Long id);

    /**
     * 删除规格并级联逻辑删除其全部版本
     */
    void deleteByIdCascade(Long id);

    /**
     * 插入一个新发布版本（insert-only，已发布版本不可变）
     */
    void createVersion(AgentSpecVersion version);

    /**
     * 查询规格的版本历史（按版本号倒序），规格不存在返回空列表
     */
    List<AgentSpecVersion> findVersionsBySpecId(Long specId);

    /**
     * 查询规格的指定版本（运行时装配按会话绑定定位不可变快照用），不存在返回 null
     */
    AgentSpecVersion findVersion(Long specId, Integer versionNo);

}
