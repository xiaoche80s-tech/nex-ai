package com.gkht.ai.nexai.module.ai.model.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.model.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ChannelPageQuery;

import java.util.List;

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

    /**
     * 启用渠道精简列表（id/名称/提供商），供模型管理页签下拉选择
     */
    List<ChannelDTO> getEnabledChannelList();

    /**
     * 渠道连通性测试：用表单当前凭据（不落库）做一次轻量真实调用，
     * 供保存前发现密钥或端点错误；编辑已存渠道且密钥留空时回退其已存密钥。
     */
    ConnectivityTestDTO testChannelConnectivity(ChannelConnectivityTestCommand command);

}
