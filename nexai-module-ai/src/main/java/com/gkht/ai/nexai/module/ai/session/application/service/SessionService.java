package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCloneCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionConfirmCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import reactor.core.publisher.Flux;

/**
 * 会话应用服务：调试会话的创建、发消息（事件流）、HITL 确认、中断、克隆重跑与查询编排。
 */
public interface SessionService {

    /**
     * 创建调试会话：绑定规格的指定已发布版本（缺省取当前默认版本）
     *
     * @return 会话编号
     */
    Long createDebugSession(DebugSessionCreateCommand command);

    /**
     * 向调试会话发送一条用户消息，返回原生 AgentEvent JSON 事件流（SSE 逐条转发）。
     *
     * <p>装配读取与轮次记录在调用线程（Servlet）同步完成；事件流的生产由运行时网关
     * 切换到弹性线程，调用方不得在流回调中做阻塞 DB 调用（spec 总体架构纪律）。</p>
     *
     * @param sessionId 会话编号
     * @param command   消息命令
     * @param userId    发起人用户编号（运行时状态存储的 userId 槽位）
     */
    Flux<String> sendDebugMessage(Long sessionId, DebugSessionMessageCommand command, Long userId);

    /**
     * 回应工具确认请求（HITL 三态：批准 / 改参数后批准 / 拒绝），返回后续事件流（SSE）。
     * 会话存在待确认的工具调用时才可调用，否则流以 SESSION_ERROR 收尾。
     *
     * @param sessionId 会话编号
     * @param command   确认回应命令
     * @param userId    回应人用户编号（须与挂起时的 userId 槽位一致——同一登录用户）
     */
    Flux<String> confirmToolCalls(Long sessionId, DebugSessionConfirmCommand command, Long userId);

    /**
     * 中断会话正在运行的事件流（幂等：无运行中的流时返回 false）。
     *
     * @param sessionId 会话编号
     * @return true 已触发中断；false 当前没有运行中的流
     */
    boolean interruptSession(Long sessionId);

    /**
     * 克隆调试会话为新调试会话（复制对话历史状态，可微调推理参数）并返回新会话编号。
     *
     * @param sourceId 源会话编号
     * @param command  克隆命令（标题与推理参数覆盖，均可空）
     * @param userId   操作人用户编号（新会话状态的 userId 槽位）
     */
    Long cloneDebugSession(Long sourceId, DebugSessionCloneCommand command, Long userId);

    /**
     * 会话分页查询
     */
    PageResult<SessionDTO> getSessionPage(SessionPageQuery query);

}
