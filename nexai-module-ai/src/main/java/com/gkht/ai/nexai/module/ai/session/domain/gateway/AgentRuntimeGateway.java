package com.gkht.ai.nexai.module.ai.session.domain.gateway;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 智能体运行时网关端口（六边形出端口，ADR-0001 沿用清单）：把 agentscope HarnessAgent 的
 * 调用面翻译为领域可用的运行时服务。
 *
 * <p>隔离约定（与 {@code ModelConnectivityGateway} 同构）：本接口与出参事件流
 * 不暴露任何 agentscope 类型——事件以 {@link RuntimeEvent} 信封交付（type 判别 +
 * 事件 JSON 载荷），调用方（应用层/控制器）零框架依赖。任何链路不 per-请求新建实例：
 * 常驻实例的构建、缓存与失效由基础设施层 {@code AgentInstanceManager} 负责。</p>
 *
 * <p>会话槽位：agentscope 以 (userId, sessionId) 定位持久化状态；装配指令
 * {@link AgentRuntimeConfig} 中携带该槽位与配置来源。调用同 (userId, sessionId) 的
 * 会话被框架按槽位串行化（FIFO 排队），不同会话并行。</p>
 */
public interface AgentRuntimeGateway {

    /**
     * 向智能体发送一条用户消息，返回全事件流（文本增量/思考块/工具调用/用量/HITL 挂起）。
     *
     * <p>事件 JSON 由基础设施层序列化（agentscope 事件类型注册进全局 ObjectMapper），
     * 载荷为 {@code {type, ...}} 结构；错误以 {@code SESSION_ERROR} 类型事件收尾，不抛异常。
     * 同 (userId, sessionId) 的并发调用由框架槽位门排队，此处不额外拒绝。</p>
     *
     * @param config 装配指令（含会话槽位与配置来源，快路径命中时仅用槽位）
     * @param content 用户消息文本
     */
    Flux<RuntimeEvent> chat(AgentRuntimeConfig config, String content);

    /**
     * OpenAI 兼容出口调用（工单 16）：以客户端全量历史（role + content）调用常驻智能体，
     * 返回 OpenAI 兼容流式 chunk 序列（事件类型 {@code OPENAI_CHUNK}，载荷 = chunk JSON）。
     *
     * <p><b>无状态出口</b>：OpenAI 协议由客户端管理历史；每次调用独立会话槽位
     * （sessionKey = 出口前缀 + requestId），实例仍常驻复用（ADR-0001：不 per-请求新建）。
     * 事件转换直用 agentscope {@code ChatCompletionsStreamingAdapter}（ADR-0001 禁自建）。
     * 错误以 {@code SESSION_ERROR} 类型事件收尾（载荷为平台错误 JSON，非 chunk 形态——
     * 调用方据此分流渲染）。</p>
     *
     * @param config    装配指令（model 字段路由的规格解析结果）
     * @param messages  客户端全量消息历史（system/user/assistant）
     * @param requestId 请求标识（OpenAI 响应 id 数据源，兼作出口会话槽位）
     */
    Flux<RuntimeEvent> chatOpenAi(AgentRuntimeConfig config, List<ChatMessageInput> messages,
                                  String requestId);

    /**
     * 对挂起中的敏感工具调用做人工审批（HITL 三态：确认/拒绝/改参数），返回续行事件流。
     *
     * <p>仅当智能体处于 ASKING 挂起态时调用有效；无挂起时返回 {@code SESSION_ERROR} 事件。
     * 拒绝陈旧/无关 toolCallId 由 agentscope {@code validateAndAcceptConfirmResults} 负责，
     * 此处不做二次校验。改参数 = 确认 + 修改后的参数（ToolCallDecision 语义）。</p>
     *
     * @param config    装配指令
     * @param decisions 每个挂起工具调用的三态决定（toolCallId 与 {@code RequireUserConfirmEvent}
     *                  推送的挂起上下文一一对应）
     */
    Flux<RuntimeEvent> confirmToolCalls(AgentRuntimeConfig config, List<ToolCallDecision> decisions);

    /**
     * 中断正在运行的会话（幂等）。按装配指令的 (userId, sessionId) 槽位定位当前流，
     * 触发框架中断旗标，流在下一个检查点停止推理并正常收尾。
     */
    boolean interrupt(AgentRuntimeConfig config);

    /**
     * 列出会话 workspace 根下某目录的文件清单（相对路径），供调试台文件栏展示。
     *
     * @param config 装配指令（需命中已装配实例以解析 workspace 根）
     * @param relativePath 相对目录，空 = 根
     */
    List<String> listWorkspaceFiles(AgentRuntimeConfig config, String relativePath);

    /**
     * 读取会话 workspace 下某文件的文本内容，供调试台查看与审计。
     *
     * @return 文件内容；路径越界（逃逸 workspace 根）或不存在时返回 null
     */
    String readWorkspaceFile(AgentRuntimeConfig config, String relativePath);

    /**
     * 加载持久化的会话上下文消息（应用层恢复会话历史用）。
     *
     * @return 按时间序的消息 JSON 快照（agentscope Msg JSON），无持久化历史时为空列表
     */
    List<Map<String, Object>> loadSessionMessages(AgentRuntimeConfig config);

    /**
     * 释放全部常驻实例（应用停机时调用）：按引用计数善后关闭 agent，清空注册表。
     */
    void closeAll();
}
