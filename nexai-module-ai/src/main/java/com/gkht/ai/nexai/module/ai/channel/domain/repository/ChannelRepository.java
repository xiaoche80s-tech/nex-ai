package com.gkht.ai.nexai.module.ai.channel.domain.repository;

import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;

/**
 * 渠道聚合仓储端口（按聚合不按表：Channel 根 + Model 实体共用一个端口）。
 *
 * <p>Model 是聚合内实体而非独立聚合——不整树加载（模型数量不定），
 * 实体级操作单条存取，挂接渠道的存在性校验由应用层负责。</p>
 */
public interface ChannelRepository {

    /**
     * 保存渠道根：无编号时插入（回填编号），有编号时更新
     *
     * @return 渠道编号
     */
    Long save(Channel channel);

    /**
     * 按编号查找渠道，不存在返回 null
     */
    Channel findById(Long id);

    /**
     * 删除渠道及其下全部模型（聚合级联）
     */
    void deleteByIdCascade(Long id);

    /**
     * 保存模型实体：无编号时插入（回填编号），有编号时更新
     *
     * @return 模型编号
     */
    Long saveModel(Model model);

    /**
     * 按编号查找模型，不存在返回 null
     */
    Model findModelById(Long id);

    /**
     * 删除单个模型
     */
    void deleteModelById(Long id);

}
