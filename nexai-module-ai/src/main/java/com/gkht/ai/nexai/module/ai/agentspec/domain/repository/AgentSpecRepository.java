package com.gkht.ai.nexai.module.ai.agentspec.domain.repository;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;

import java.util.List;

/**
 * 智能体规格聚合仓储端口（按聚合不按表）：规格主体（草稿/当前版本指针）与
 * 其下的不可变版本快照同属一个聚合边界，事务内一起持久化。
 *
 * <p>版本簿记（max+1 计算、快照插入、指针推进）收拢在 {@link #persistPublication}
 * 单方法内原子完成；列表查询走轻量读写分离（应用服务经 Mapper 直查转 DTO），不经本端口。</p>
 */
public interface AgentSpecRepository {

    /**
     * 保存聚合主体：无编号时插入（回填编号），已有编号时更新（草稿与当前版本指针）
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
     * 读取规格下的全部版本快照（按版本号升序），未发布返回空列表
     */
    List<AgentSpecVersion> listVersions(Long specId);

    /**
     * 按规格 + 版本号读取单条版本快照（按需读取：config 为全量 JSON，
     * 生效快照解析/版本预览/切换目标解析共用），不存在返回 null
     */
    AgentSpecVersion findVersion(Long specId, Integer versionNo);

    /**
     * 发布落库（调用方事务内原子执行）：计算新版本号（现有最大 + 1，首次 = 1）、
     * 经聚合根固化不可变快照并推进当前版本指针、快照插入与主体更新一起持久化。
     *
     * @param spec 已读取的规格聚合
     * @param note 发布备注，可空
     * @return 新版本号
     * @throws IllegalStateException 无草稿或草稿缺模型引用（聚合根发布校验）
     */
    Integer persistPublication(AgentSpec spec, String note);

}
