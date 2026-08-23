package com.gkht.ai.nexai.module.ai.support;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 脚本化 Mock 模型（实现 agentscope {@link Model} 接口）：不依赖真实 LLM 的全链验证替身。
 * 每个流式调用消费脚本的下一步——回复文本 / 携带工具调用 / 结束；记录每次收到的消息
 * （断言系统提示注入、跨轮次上下文、工具结果回填）。脚本耗尽后抛错（暴露意外调用）。
 *
 * <p>注入方式：测试 stub {@code ChatModelFactory}（create() 返回本实例），装配链其余
 * （ModelRegistry、agent 构建、PG 状态存储、事件流）全真实——接缝只打在模型上，
 * 符合 spec「主接缝 = HTTP API 层，模型用 Mock 模型」的辅助接缝约定。</p>
 */
public class FakeChatModel implements Model {

    /** 每次 stream() 消费的脚本步骤 */
    private final List<Step> script = new CopyOnWriteArrayList<>();
    /** 收到的消息（断言用） */
    private final List<List<io.agentscope.core.message.Msg>> receivedMessages =
            new CopyOnWriteArrayList<>();
    private final String modelName;

    private FakeChatModel(String modelName, List<Step> script) {
        this.modelName = modelName;
        this.script.addAll(script);
    }

    public static Builder script() {
        return new Builder();
    }

    @Override
    public Flux<ChatResponse> stream(List<io.agentscope.core.message.Msg> messages,
                                     List<ToolSchema> tools, GenerateOptions options) {
        receivedMessages.add(List.copyOf(messages));
        int index = scriptIndex();
        if (index >= script.size()) {
            // 脚本耗尽：返回空收尾响应（ReAct 循环的收尾模型调用——工具执行后需再次确认是否结束，
            // 调用次数不确定，空响应让循环自然结束而非抛错暴露）
            return Flux.just(ChatResponse.builder()
                    .id("fake-" + System.nanoTime())
                    .content(List.of())
                    .usage(ChatUsage.builder().inputTokens(1).outputTokens(1).build())
                    .finishReason("stop")
                    .build());
        }
        return Flux.just(script.get(index).toResponse());
    }

    private final java.util.concurrent.atomic.AtomicInteger scriptCursor =
            new java.util.concurrent.atomic.AtomicInteger();

    private int scriptIndex() {
        return scriptCursor.getAndIncrement();
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    /** 收到的消息（每轮一次调用） */
    public List<List<io.agentscope.core.message.Msg>> getReceivedMessages() {
        return receivedMessages;
    }

    /** 脚本步骤：回复文本（可携带工具调用块） */
    public interface Step {
        ChatResponse toResponse();
    }

    /** 回复纯文本的步骤 */
    private record TextStep(String text) implements Step {
        @Override
        public ChatResponse toResponse() {
            return ChatResponse.builder()
                    .id("fake-" + System.nanoTime())
                    .content(List.of(TextBlock.builder().text(text).build()))
                    .usage(ChatUsage.builder().inputTokens(10).outputTokens(5).build())
                    .finishReason("stop")
                    .build();
        }
    }

    /** 回复文本 + 工具调用的步骤 */
    private record ToolStep(String text, String toolName, Map<String, Object> args) implements Step {
        @Override
        public ChatResponse toResponse() {
            io.agentscope.core.message.ToolUseBlock toolUse =
                    io.agentscope.core.message.ToolUseBlock.builder()
                            .id("call-" + System.nanoTime())
                            .name(toolName)
                            .input(args == null ? Map.of() : args)
                            .build();
            List<ContentBlock> blocks = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                blocks.add(TextBlock.builder().text(text).build());
            }
            blocks.add(toolUse);
            return ChatResponse.builder()
                    .id("fake-" + System.nanoTime())
                    .content(blocks)
                    .usage(ChatUsage.builder().inputTokens(10).outputTokens(5).build())
                    .finishReason("tool_calls")
                    .build();
        }
    }

    public static class Builder {

        private final List<Step> steps = new ArrayList<>();
        private String modelName = "fake-chat-model";

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** 回复纯文本 */
        public Builder reply(String text) {
            steps.add(new TextStep(text));
            return this;
        }

        /** 回复文本 + 工具调用 */
        public Builder callTool(String toolName, Map<String, Object> args) {
            steps.add(new ToolStep("", toolName, args));
            return this;
        }

        /** 回复文本 + 工具调用（带前置说明文本） */
        public Builder textAndTool(String text, String toolName, Map<String, Object> args) {
            steps.add(new ToolStep(text, toolName, args));
            return this;
        }

        public FakeChatModel build() {
            return new FakeChatModel(modelName, steps);
        }
    }

}
