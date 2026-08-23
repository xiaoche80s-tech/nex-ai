package com.gkht.ai.nexai.module.ai.session.interfaces.controller.admin.session;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.security.core.util.SecurityFrameworkUtils;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionConfirmCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.PendingConfirmationDTO;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.application.service.SessionService;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 调试会话控制器（包名含 controller.admin，挂 /admin-api）。SSE 端点在 MVC 栈下经
 * {@link SseEmitter} 桥接 {@link Flux<RuntimeEvent>}（agentscope AG-UI MVC starter 同款做法）；
 * 事件以 data 帧发送，载荷为 agentscope 事件 JSON（含 type 判别字段），前端按类型化事件流渲染。
 */
@Tag(name = "管理后台 - 调试会话")
@RestController
@RequestMapping("/ai/session")
@Validated
public class DebugSessionController {

    @Resource
    private SessionService sessionService;

    @PostMapping("/debug/create")
    @Operation(summary = "发起调试会话", description = "绑定规格与版本（null = 当前版本），生成会话业务键")
    @PreAuthorize("@ss.hasPermission('ai:session:create')")
    public CommonResult<Long> createDebugSession(@Valid @RequestBody DebugSessionCreateCommand command) {
        return success(sessionService.createDebugSession(command, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping(value = "/{id}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送调试消息", description = "SSE 全事件流：文本增量/思考块/工具调用/用量；错误以 SESSION_ERROR 事件收尾")
    @PreAuthorize("@ss.hasPermission('ai:session:message')")
    public SseEmitter sendDebugMessage(@PathVariable("id") Long id,
                                       @Valid @RequestBody DebugSessionMessageCommand command) {
        Flux<RuntimeEvent> events = sessionService.sendDebugMessage(id, command,
                SecurityFrameworkUtils.getLoginUserId());
        return bridge(events);
    }

    @PostMapping(value = "/{id}/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "HITL 审批", description = "对挂起中的敏感工具调用三态审批（确认/拒绝/改参数），返回续行事件流")
    @PreAuthorize("@ss.hasPermission('ai:session:confirm')")
    public SseEmitter confirmDebugCalls(@PathVariable("id") Long id,
                                        @Valid @RequestBody DebugSessionConfirmCommand command) {
        Flux<RuntimeEvent> events = sessionService.confirmDebugCalls(id, command,
                SecurityFrameworkUtils.getLoginUserId());
        return bridge(events);
    }

    @PostMapping("/{id}/interrupt")
    @Operation(summary = "中断调试会话", description = "幂等；中断当前运行流并正常收尾")
    @PreAuthorize("@ss.hasPermission('ai:session:interrupt')")
    public CommonResult<Boolean> interruptDebugSession(@PathVariable("id") Long id) {
        return success(sessionService.interruptDebugSession(id, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得调试会话分页", description = "可按规格与状态过滤")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<PageResult<SessionDTO>> getSessionPage(@Validated SessionPageQuery query) {
        return success(sessionService.getSessionPage(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得会话详情", description = "含规格业务编码补充")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<SessionDTO> getSession(@PathVariable("id") Long id) {
        return success(sessionService.getSession(id));
    }

    @GetMapping("/{id}/pending")
    @Operation(summary = "获得挂起审批上下文", description = "ASKING 状态会话恢复时渲染审批卡片")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<List<PendingConfirmationDTO>> getPendingConfirmations(@PathVariable("id") Long id) {
        return success(sessionService.getPendingConfirmations(id,
                SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "加载会话历史消息", description = "重开调试台恢复（经 AgentStateStore 读槽位上下文，工单 09）")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<List<java.util.Map<String, Object>>> loadSessionHistory(@PathVariable("id") Long id) {
        return success(sessionService.loadSessionHistory(id, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{id}/workspace/files")
    @Operation(summary = "列出会话 workspace 文件", description = "调试台文件栏（工单 07）；相对路径空 = 根目录")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<List<String>> listWorkspaceFiles(@PathVariable("id") Long id,
                                                         @RequestParam(value = "path", required = false) String path) {
        return success(sessionService.listSessionWorkspaceFiles(id, path,
                SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{id}/workspace/file")
    @Operation(summary = "读取会话 workspace 文件内容", description = "调试台查看（工单 07）；越界/不存在返回 null")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<String> readWorkspaceFile(@PathVariable("id") Long id,
                                                  @RequestParam("path") String path) {
        return success(sessionService.readSessionWorkspaceFile(id, path,
                SecurityFrameworkUtils.getLoginUserId()));
    }

    /**
     * Flux<RuntimeEvent> → SseEmitter 桥接：每个事件发一帧 data（载荷即事件 JSON），
     * 流正常结束 complete，异常发 SESSION_ERROR 帧后 complete（不挂死连接）。
     */
    private SseEmitter bridge(Flux<RuntimeEvent> events) {
        SseEmitter emitter = new SseEmitter(0L); // 无超时（智能体轮次可能较长）
        events.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe(
                        event -> send(emitter, event),
                        error -> {
                            sendError(emitter, error.getMessage());
                            emitter.complete();
                        },
                        emitter::complete);
        return emitter;
    }

    private void send(SseEmitter emitter, RuntimeEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type().name())
                    .data(event.payload()));
        } catch (IOException ex) {
            // 客户端断开：取消订阅（框架 doFinally 释放常驻实例引用）
            emitter.completeWithError(ex);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("SESSION_ERROR")
                    .data("{\"type\":\"SESSION_ERROR\",\"message\":\""
                            + (message == null ? "未知错误" : message.replace("\"", "'")) + "\"}"));
        } catch (IOException ignored) {
            // 客户端已断开，忽略
        }
    }

}
