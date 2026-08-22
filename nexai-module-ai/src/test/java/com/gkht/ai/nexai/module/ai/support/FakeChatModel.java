package com.gkht.ai.nexai.module.ai.support;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * S2 测试接缝：agentscope {@link Model} 的脚本化替身（spec Testing Decisions——全平台唯一新接缝）。
 *
 * <p>按「推理轮次」编排应答：ReActAgent 每调用一次 {@code stream}（一轮推理）消费脚本中的下一步——
 * {@code reply} 产生文本应答（结束推理循环），{@code callTool} 产生工具调用请求
 * （agent 执行真实注册的工具后带着工具结果发起下一轮推理，再消费下一步）。
 * 每步以单个 ChatResponse 交付全量内容块（ReActAgent 对全量块同样产生
 * START/DELTA/END 完整事件序列），使「发消息 → 事件流 → 工具调用 → 最终结果」
 * 可确定性验证，不碰真实 LLM。脚本耗尽仍被调用即测试预期失败，快速暴露断言错误。</p>
 *
 * <p>同时记录每轮收到的消息列表（{@link #getReceivedMessages()}），供断言系统提示注入、
 * 多轮上下文延续与工具结果回填等行为。{@code stepDelay} 给每步应答施加发射延迟，
 * 使中断（InterruptControl）测试能在轮次间触发并观察到流的及时收尾（工单 08）。</p>
 */
public final class FakeChatModel implements Model {

    private final List<ChatResponse> script;
    private final Duration stepDelay;
    private final AtomicInteger nextStep = new AtomicInteger();
    private final List<List<Msg>> receivedMessages = new CopyOnWriteArrayList<>();

    private FakeChatModel(List<ChatResponse> script, Duration stepDelay) {
        this.script = List.copyOf(script);
        this.stepDelay = stepDelay;
    }

    /**
     * 脚本编排入口
     */
    public static ScriptBuilder script() {
        return new ScriptBuilder();
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        receivedMessages.add(List.copyOf(messages));
        int step = nextStep.getAndIncrement();
        if (step >= script.size()) {
            return Flux.error(new IllegalStateException(
                    "FakeChatModel 脚本已耗尽（共 " + script.size() + " 步），第 " + (step + 1)
                            + " 次推理调用未编排——请检查用例编排与被测行为的匹配"));
        }
        return stepDelay == null ? Flux.just(script.get(step))
                : Flux.just(script.get(step)).delayElements(stepDelay);
    }

    @Override
    public String getModelName() {
        return "fake-chat-model";
    }

    /**
     * 每轮推理收到的消息快照（含系统提示与历史轮次），按下标即轮次
     */
    public List<List<Msg>> getReceivedMessages() {
        return List.copyOf(receivedMessages);
    }

    /**
     * 脚本编排 DSL
     */
    public static final class ScriptBuilder {

        private static final ChatUsage USAGE = new ChatUsage(11, 7, 0.01d);

        private final List<ChatResponse> steps = new ArrayList<>();
        private Duration stepDelay;

        /**
         * 本轮推理以文本应答（ReAct 收到无工具调用的回复即结束循环并产出 AgentResult）
         */
        public ScriptBuilder reply(String text) {
            return step(TextBlock.builder().text(text).build());
        }

        /**
         * 本轮推理请求调用工具：agent 执行真实注册的同名工具后，携工具结果发起下一轮推理。
         * 参数 JSON 放 content（与真实模型解析器一致——agentscope 的参数校验与执行
         * 均从 content 解析，input 仅作结构化回填）
         */
        public ScriptBuilder callTool(String toolName, Map<String, Object> input) {
            return step(ToolUseBlock.builder()
                    .id("fake-call-" + steps.size())
                    .name(toolName)
                    .input(Map.of())
                    .content(io.agentscope.core.util.JsonUtils.getJsonCodec().toJson(input))
                    .build());
        }

        /**
         * 每步应答延迟发射（模拟慢模型）：中断测试用——延迟期间触发 InterruptControl，
         * 流应在下一个检查点收尾而非跑完全部脚本
         */
        public ScriptBuilder stepDelay(Duration delay) {
            this.stepDelay = delay;
            return this;
        }

        private ScriptBuilder step(ContentBlock block) {
            steps.add(ChatResponse.builder()
                    .id("fake-response-" + steps.size())
                    .content(List.of(block))
                    .usage(USAGE)
                    .finishReason("stop")
                    .build());
            return this;
        }

        public FakeChatModel build() {
            return new FakeChatModel(steps, stepDelay);
        }

    }

}
