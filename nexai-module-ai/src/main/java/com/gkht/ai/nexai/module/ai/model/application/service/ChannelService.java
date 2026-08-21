package com.gkht.ai.nexai.module.ai.model.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ChannelPageQuery;

/**
 * 渠道管理应用服务：编排聚合行为，密钥等安全语义在领域与转换层保证。
 */
public interface ChannelService {

    /**
     * 创建租户渠道，返回渠道编号
     */
    Long createChannel(ChannelCreateCommand command);

    /**
     * 更新渠道基础信息；apiKey 为空时保留原密钥
     */
    void updateChannel(ChannelUpdateCommand command);

    /**
     * 启用/停用渠道
     */
    void updateChannelStatus(ChannelUpdateStatusCommand command);

    /**
     * 删除渠道（逻辑删除）
     */
    void deleteChannel(Long id);

    /**
     * 渠道分页查询
     */
    PageResult<ChannelDTO> getChannelPage(ChannelPageQuery query);

    /**
     * 渠道详情
     */
    ChannelDTO getChannel(Long id);

}
