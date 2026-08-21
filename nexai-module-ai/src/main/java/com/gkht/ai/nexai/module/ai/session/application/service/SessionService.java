package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import reactor.core.publisher.Flux;

/**
 * 会话应用服务：调试会话的创建、发消息（事件流）与查询编排。
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
     * 会话分页查询
     */
    PageResult<SessionDTO> getSessionPage(SessionPageQuery query);

}
