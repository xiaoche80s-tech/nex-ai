package com.gkht.ai.nexai.module.ai.model.domain.gateway;

import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ConnectivityResult;

/**
 * 模型连通性探测端口：用渠道凭据对指定模型做一次轻量真实调用。
 *
 * <p>domain 层经此端口隔离 agentscope 类型；实现位于 infrastructure（按提供商构造 ChatModel 外呼）。
 * 探测不抛异常——成败与原因一律封装在 {@link ConnectivityResult} 中。</p>
 */
public interface ModelConnectivityGateway {

    /**
     * @param channel 渠道聚合根（携带提供商类型、端点与凭据）
     * @param modelId 模型标识（传给提供商的 ID）
     * @return 探测结果（成败/耗时/说明）
     */
    ConnectivityResult probe(Channel channel, String modelId);

}
