package com.gkht.ai.nexai.module.ai.session.domain.gateway;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能体运行时端口：按装配指令实例化 agent 并转发用户消息，返回事件流。
 *
 * <p>domain 不 import agentscope 类型（ADR-0001）：事件流以「原生 AgentEvent 的 JSON 字符串」
 * 逐条交付（序列化经 agentscope 自带 codec，Jackson 2，ADR-0005 保真优先），
 * domain 只依赖 Reactor（纯库，与 slf4j 同定位）。实现侧负责把流的生产切到弹性线程，
 * 并保证流终结后释放 agent 资源。</p>
 *
 * <p>实现侧须保证同一会话（sessionKey）同一时刻至多一条事件流在跑：
 * 运行中再次发起（发消息 / 确认回应）抛 {@link com.gkht.ai.nexai.module.ai.session.domain.exception.SessionRunningException}。</p>
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

    /**
     * 回应工具确认请求（HITL 三态）：携带确认决定重新进入运行时，
     * 返回事件流覆盖 USER_CONFIRM_RESULT → 工具执行（或拒绝结果）→ 后续推理 → AGENT_END。
     * 待确认调用不存在或决定与之不匹配时，流以 SESSION_ERROR 事件收尾。
     *
     * @param config    运行时装配指令（须与挂起时同槽位寻址，才能命中待确认状态）
     * @param decisions 对每条待确认工具调用的三态决定（批准 / 改参数后批准 / 拒绝）
     */
    Flux<String> confirmToolCalls(AgentRuntimeConfig config, List<ToolCallDecision> decisions);

    /**
     * 中断会话正在运行的事件流（InterruptControl 旗标）：运行中的流将在当前检查点
     * 停止推理并以正常事件序列收尾（写入中断恢复消息后 AGENT_END）。
     *
     * @param sessionKey 会话标识
     * @return true 表示已对运行中的流触发中断；false 表示该会话当前没有运行中的流（幂等）
     */
    boolean interrupt(String sessionKey);

    /**
     * 复制会话的运行时对话状态（克隆重跑用）：把源会话槽位的全部状态搬到目标槽位，
     * 使新会话带着源会话的完整上下文继续。
     *
     * @param fromUserId    源状态存储 userId 槽位
     * @param fromSessionKey 源会话标识
     * @param toUserId      目标状态存储 userId 槽位
     * @param toSessionKey  目标会话标识
     * @return true 复制成功；false 源会话无状态（例如从未发过消息），目标保持空白
     */
    boolean copySessionState(String fromUserId, String fromSessionKey,
                             String toUserId, String toSessionKey);

}
