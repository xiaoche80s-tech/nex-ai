package com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.channel.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ConnectivityResult;
import com.gkht.ai.nexai.module.ai.shared.util.RootCauses;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 连通性探测网关（agentscope 适配器，ADR-0001 直用）：经 {@link ChatModelFactory} 按渠道提供商
 * 构造 ChatModel（与运行时装配走同一套模型构造路径），以 maxTokens=1 的单条消息做一次
 * 轻量真实调用——能收到任一响应即证明端点、凭据与模型标识可用。
 */
@Component
public class AgentscopeModelConnectivityGateway implements ModelConnectivityGateway {

    /** 单次探测整体超时：网络异常或网关挂死时及时止损 */
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);
    /** 探测消息的输出上限：Anthropic 要求 maxTokens 必填，统一为 1 保证各提供商一致 */
    static final int PROBE_MAX_TOKENS = 1;
    /** 失败信息截断长度：调用方错误消息可能携带长响应体 */
    private static final int MAX_MESSAGE_LENGTH = 500;

    private static final String PROBE_USER_ID = "nexai-connectivity-probe";

    @Resource
    private ChatModelProvider chatModelProvider;

    @Override
    public ConnectivityResult probe(Channel channel, String modelId) {
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = chatModelProvider.create(channel, modelId)
                    .stream(List.of(new UserMessage(PROBE_USER_ID, "ping")), null,
                            new GenerateOptions.Builder().maxTokens(PROBE_MAX_TOKENS).build())
                    .next()
                    .block(PROBE_TIMEOUT);
            long elapsedMs = elapsedMs(startNanos);
            if (response == null) {
                // 理论上流有错误都会走异常分支；空响应按失败兜底
                return ConnectivityResult.failure(elapsedMs, "未收到任何模型响应");
            }
            return ConnectivityResult.success(elapsedMs,
                    String.format("连通正常（响应 ID：%s）", response.getId()));
        } catch (Exception ex) {
            return ConnectivityResult.failure(elapsedMs(startNanos),
                    RootCauses.rootMessage(ex, MAX_MESSAGE_LENGTH));
        }
    }

    private long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

}
