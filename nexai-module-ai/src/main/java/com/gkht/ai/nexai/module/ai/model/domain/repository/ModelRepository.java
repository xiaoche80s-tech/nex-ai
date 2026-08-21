package com.gkht.ai.nexai.module.ai.model.domain.repository;

import com.gkht.ai.nexai.module.ai.model.domain.model.Model;

import java.util.Collection;
import java.util.List;

/**
 * 模型 Repository 端口。Model 独立成聚合根（外部仅引用 channelId），与 Channel 聚合并列于 model 聚合目录。
 */
public interface ModelRepository {

    /**
     * 保存聚合：id 为 null 走插入，否则走更新
     *
     * @return 落库后的编号
     */
    Long save(Model model);

    /**
     * 按编号查找，不存在返回 null
     */
    Model findById(Long id);

    /**
     * 按编号批量查找（跨聚合只读补充信息用），不存在的编号静默跳过
     */
    List<Model> findByIds(Collection<Long> ids);

    /**
     * 逻辑删除
     */
    void deleteById(Long id);

    /**
     * 同渠道下模型标识是否已被（其他模型）占用
     *
     * @param excludeId 排除自身的编号（更新场景），新建传 null
     */
    boolean existsByChannelIdAndModelId(Long channelId, String modelId, Long excludeId);

}
