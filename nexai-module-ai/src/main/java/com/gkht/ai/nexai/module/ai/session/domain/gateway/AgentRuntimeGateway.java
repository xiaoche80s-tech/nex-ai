package com.gkht.ai.nexai.module.ai.session.domain.gateway;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import reactor.core.publisher.Flux;

/**
 * 智能体运行时端口：按装配指令实例化 agent 并转发用户消息，返回事件流。
 *
 * <p>domain 不 import agentscope 类型（ADR-0001）：事件流以「原生 AgentEvent 的 JSON 字符串」
 * 逐条交付（序列化经 agentscope 自带 codec，Jackson 2，ADR-0005 保真优先），
 * domain 只依赖 Reactor（纯库，与 slf4j 同定位）。实现侧负责把流的生产切到弹性线程，
 * 并保证流终结后释放 agent 资源。</p>
 */
public interface AgentRuntimeGateway {

    /**
     * 发送一条用户消息并返回事件 JSON 流（每条一个完整 AgentEvent JSON，
     * 序列覆盖 AGENT_START → MODEL_CALL_* / TEXT_BLOCK_* → AGENT_RESULT → AGENT_END）
     *
     * @param config  运行时装配指令（渠道/模型/系统提示/推理参数/会话寻址）
     * @param message 用户消息内容，不能为空白
     */
    Flux<String> chat(AgentRuntimeConfig config, String message);

}
