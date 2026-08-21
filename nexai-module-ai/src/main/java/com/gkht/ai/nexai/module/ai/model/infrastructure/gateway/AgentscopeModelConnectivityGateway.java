package com.gkht.ai.nexai.module.ai.model.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.model.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ConnectivityResult;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.gemini.GeminiChatModel;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 连通性探测网关（agentscope 适配器）：按渠道提供商构造 ChatModel，
 * 以 maxTokens=1 的单条消息做一次轻量真实调用——能收到任一响应即证明
 * 端点、凭据与模型标识可用，与运行时装配（工单 06）走同一套模型构造路径。
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

    @Override
    public ConnectivityResult probe(Channel channel, String modelId) {
        long startNanos = System.nanoTime();
        try {
            io.agentscope.core.model.Model chatModel = createChatModel(channel, modelId);
            ChatResponse response = chatModel
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
            return ConnectivityResult.failure(elapsedMs(startNanos), rootMessage(ex));
        }
    }

    /**
     * 按提供商构造 agentscope ChatModel。渠道未配置 baseUrl 时各提供商用自身默认端点。
     */
    private io.agentscope.core.model.Model createChatModel(Channel channel, String modelId) {
        ChannelProvider provider = channel.getProvider();
        String baseUrl = channel.getBaseUrl();
        String apiKey = channel.getApiKey();
        // 每请求新建 Model 实例（agentscope 线程模型：单 agent 单 session 串行，探测不复用）
        return switch (provider) {
            case OPENAI, OPENAI_COMPAT -> OpenAIChatModel.builder()
                    .apiKey(apiKey).baseUrl(baseUrl).modelName(modelId).stream(true)
                    .build();
            case DASHSCOPE -> DashScopeChatModel.builder()
                    .apiKey(apiKey).baseUrl(baseUrl).modelName(modelId).stream(true)
                    .build();
            case ANTHROPIC -> AnthropicChatModel.builder()
                    .apiKey(apiKey).baseUrl(baseUrl).modelName(modelId).stream(true)
                    .build();
            case GEMINI -> GeminiChatModel.builder()
                    .apiKey(apiKey).baseUrl(baseUrl).modelName(modelId).streamEnabled(true)
                    .build();
            case OLLAMA -> OllamaChatModel.builder()
                    .baseUrl(baseUrl).modelName(modelId).stream(true)
                    .build();
        };
    }

    private long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    /**
     * 逐层解包取根因消息（Reactor 会用 RuntimeException 包装底层 HTTP 异常），
     * 带异常类型名便于分辨鉴权/超时/模型名错误，并截断到展示上限。
     */
    private String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        } else {
            message = current.getClass().getSimpleName() + ": " + message;
        }
        return message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) + "…" : message;
    }

}
