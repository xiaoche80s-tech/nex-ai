package com.gkht.ai.nexai.module.ai.session.interfaces.controller.admin.session;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.security.core.util.SecurityFrameworkUtils;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.application.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 调试会话控制器。包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 *
 * <p>发消息端点为 Reactor → Servlet 桥接：方法返回冷 {@link Flux}，Spring MVC 以 SSE 逐条
 * 写出（spike 验证过的路径）；事件 JSON 由 agentscope 原生栈序列化（ADR-0005），本层零转换。</p>
 */
@Tag(name = "管理后台 - 调试会话")
@RestController
@RequestMapping("/ai/session")
@Validated
public class DebugSessionController {

    @Resource
    private SessionService sessionService;

    @PostMapping("/debug/create")
    @Operation(summary = "创建调试会话", description = "绑定规格的已发布版本（缺省取当前默认版本），返回会话编号")
    @PreAuthorize("@ss.hasPermission('ai:session:create')")
    public CommonResult<Long> createDebugSession(@Valid @RequestBody DebugSessionCreateCommand command) {
        return success(sessionService.createDebugSession(command));
    }

    @PostMapping(value = "/{id}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "发送消息（SSE 事件流）",
            description = "按会话绑定的规格版本装配 agentscope 运行时并发送用户消息，"
                    + "流式返回原生 AgentEvent JSON（AGENT_START → MODEL_CALL/TEXT_BLOCK/… → AGENT_RESULT → AGENT_END）；"
                    + "运行中出错以 type=SESSION_ERROR 的单条事件收尾。演示：curl -N -X POST .../admin-api/ai/session/{id}/message "
                    + "-H 'Content-Type: application/json' -d '{\"content\":\"你好\"}'")
    @Parameter(name = "id", description = "会话编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:session:message')")
    public Flux<String> sendMessage(@PathVariable("id") Long id,
                                    @Valid @RequestBody DebugSessionMessageCommand command) {
        return sessionService.sendDebugMessage(id, command, SecurityFrameworkUtils.getLoginUserId());
    }

    @GetMapping("/page")
    @Operation(summary = "获得会话分页", description = "调试台左侧会话列表数据源")
    @PreAuthorize("@ss.hasPermission('ai:session:query')")
    public CommonResult<PageResult<SessionDTO>> getSessionPage(@Validated SessionPageQuery query) {
        return success(sessionService.getSessionPage(query));
    }

}
