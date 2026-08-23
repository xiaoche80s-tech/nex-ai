package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionConfirmCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.PendingConfirmationDTO;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 会话应用服务：调试会话的创建、发消息（SSE 事件流）、HITL 审批、中断、列表与恢复。
 * 编排跨聚合只读（规格版本快照/渠道/模型）与运行时网关端口；事件流以
 * {@link RuntimeEvent} 信封交付，控制器零 agentscope 依赖。
 */
public interface SessionService {

    /**
     * 发起调试会话（绑定规格与版本，生成会话业务键）
     *
     * @return 会话编号
     */
    Long createDebugSession(DebugSessionCreateCommand command, Long userId);

    /**
     * 发送消息，返回 SSE 事件流（AGENT_START → … → AGENT_END / SESSION_ERROR）。
     * 消息轮次落库（会话聚合），HITL 挂起时状态置 ASKING 并持久化挂起上下文。
     */
    Flux<RuntimeEvent> sendDebugMessage(Long sessionId, DebugSessionMessageCommand command, Long userId);

    /**
     * HITL 三态审批（确认/拒绝/改参数），返回续行事件流。
     * 无挂起时返回 SESSION_ERROR 事件；审批后清除会话挂起上下文。
     */
    Flux<RuntimeEvent> confirmDebugCalls(Long sessionId, DebugSessionConfirmCommand command, Long userId);

    /**
     * 中断正在运行的会话（幂等）
     */
    Boolean interruptDebugSession(Long sessionId, Long userId);

    /**
     * 会话分页列表（MVP 调试会话，可按规格过滤）
     */
    PageResult<SessionDTO> getSessionPage(SessionPageQuery query);

    /**
     * 会话详情（含规格业务编码补充）
     */
    SessionDTO getSession(Long id);

    /**
     * 读取会话挂起上下文（ASKING 状态恢复时渲染审批卡片）
     */
    List<PendingConfirmationDTO> getPendingConfirmations(Long sessionId, Long userId);

    /**
     * 加载会话历史消息（重开调试台恢复，工单 09：经 AgentStateStore 读槽位上下文）。
     * 返回按时间序的消息 JSON Map 快照（agentscope Msg 序列化形态）。
     */
    List<Map<String, Object>> loadSessionHistory(Long sessionId, Long userId);

    /**
     * 列出会话 workspace 根下某目录的文件（调试台文件栏，工单 07）
     */
    List<String> listSessionWorkspaceFiles(Long sessionId, String relativePath, Long userId);

    /**
     * 读取会话 workspace 下某文件内容（调试台查看，工单 07）；不存在/越界返回 null
     */
    String readSessionWorkspaceFile(Long sessionId, String relativePath, Long userId);

}
