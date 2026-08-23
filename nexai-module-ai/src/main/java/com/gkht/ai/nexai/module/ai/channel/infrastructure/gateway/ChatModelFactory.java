package com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.gemini.GeminiChatModel;
import io.agentscope.extensions.model.ollama.OllamaChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.stereotype.Component;

/**
 * 按渠道提供商构造 agentscope ChatModel 的共用工厂（ADR-0001：模型构造直用 agentscope）。
 *
 * <p>渠道连通性探测（本聚合）与运行时装配（session 聚合，工单 05）共用同一条
 * 模型构造路径——探测通过即等价于装配可用。渠道未配置 baseUrl 时各提供商用自身默认端点。</p>
 */
@Component
public class ChatModelFactory {

    /**
     * 构造指定渠道下某模型的 ChatModel 实例。
     * 每次调用新建实例（agentscope 线程模型：单 agent 单 session 串行，实例不跨会话复用）。
     */
    public Model create(Channel channel, String modelId) {
        ChannelProvider provider = channel.getProvider();
        String baseUrl = channel.getBaseUrl();
        String apiKey = channel.getApiKey();
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

}
