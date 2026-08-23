package com.gkht.ai.nexai.module.ai.channel.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ChannelPageQuery;

import java.util.List;

/**
 * 渠道应用服务：CRUD、启停与连通性探测（BYOK）。
 */
public interface ChannelService {

    /** 创建渠道（归属固定租户侧），返回编号 */
    Long createChannel(ChannelCreateCommand command);

    /** 更新渠道基础信息（apiKey 为 null 保留原密钥） */
    void updateChannel(ChannelUpdateCommand command);

    /** 启停渠道 */
    void updateChannelStatus(ChannelUpdateStatusCommand command);

    /** 删除渠道及其下全部模型 */
    void deleteChannel(Long id);

    /** 分页查询（轻量读写分离） */
    PageResult<ChannelDTO> getChannelPage(ChannelPageQuery query);

    /** 渠道详情（密钥脱敏） */
    ChannelDTO getChannel(Long id);

    /** 启用渠道简要列表（模型表单的渠道下拉） */
    List<ChannelDTO> getEnabledChannelList();

    /** 连通性探测：表单凭据即测不落库；携带 channelId 且密钥留空时回退已存密钥 */
    ConnectivityTestDTO testChannelConnectivity(ChannelConnectivityTestCommand command);

}
