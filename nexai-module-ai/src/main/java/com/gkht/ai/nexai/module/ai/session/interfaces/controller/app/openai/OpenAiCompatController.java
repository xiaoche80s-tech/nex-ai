package com.gkht.ai.nexai.module.ai.session.interfaces.controller.app.openai;

import com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey;
import com.gkht.ai.nexai.module.ai.session.application.service.OpenAiCompatService;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import io.agentscope.core.chat.completions.model.ChatCompletionsRequest;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.API_KEY_SPEC_FORBIDDEN;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.API_KEY_UNAUTHORIZED;

/**
 * OpenAI 兼容出口（工单 16，ADR-0001 直用 ChatCompletionsStreamingAdapter）：
 * {@code POST /app-api/ai/openai/chat/completions}（流式）——自有应用改个 base URL 即接入
 * （base URL = {@code http://<host>:48080/app-api/ai/openai}）。
 *
 * <p>包名含 controller.app 段，由 web starter 包通配符规则挂载 /app-api 前缀。
 * 认证由 {@code ApiKeyAuthFilter} 把守（路径已 permitAll，租户 API Key 即租户凭证）；
 * model 字段路由到智能体（specCode）。请求/响应模型直用 agentscope 协议类型
 * （纯 Jackson POJO，入口层协议面）。仅支持流式（stream=true）——非流式返回错误。
 * OpenAI 客户端的历史消息（role: system/user/assistant）透传；tool 消息 MVP 不支持。</p>
 */
@RestController
@RequestMapping("/ai/openai")
public class OpenAiCompatController {

    /** OpenAI 流式协议收尾标记 */
    private static final String DONE_MARKER = "[DONE]";
    /** SSE 帧媒体类型（显式 UTF-8：默认转换器对无 charset 的 JSON 用 ISO-8859-1 写串） */
    private static final MediaType SSE_FRAME_MEDIA_TYPE =
            MediaType.parseMediaType("application/json; charset=UTF-8");

    @Resource
    private OpenAiCompatService openAiCompatService;

    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object chatCompletions(@RequestBody ChatCompletionsRequest request,
                                  HttpServletRequest servletRequest) {
        TenantApiKey apiKey = requireAuthenticatedKey(servletRequest);
        if (!Boolean.TRUE.equals(request.getStream())) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of(
                    "message", "仅支持流式调用（stream=true）",
                    "type", "invalid_request_error")));
        }
        String specCode = request.getModel();
        if (apiKey != null && !apiKey.allowsSpec(specCode)) {
            return ResponseEntity.status(403).body(Map.of("error", Map.of(
                    "message", "API Key 不在目标智能体的放行规格范围内",
                    "type", "invalid_request_error", "code", "spec_not_allowed")));
        }
        String requestId = UUID.randomUUID().toString();
        List<ChatMessageInput> messages = request.getMessages() == null ? List.of()
                : request.getMessages().stream()
                        .map(message -> ChatMessageInput.of(
                                message.getRole() == null ? "user" : message.getRole(),
                                message.getContent() == null ? "" : message.getContent()))
                        .toList();
        return bridge(openAiCompatService.streamChatCompletions(specCode, messages, requestId));
    }

    /** 认证兜底：过滤器已校验（attribute 携带 Key 聚合）；缺失即配置性缺失（放行规则失效） */
    private static TenantApiKey requireAuthenticatedKey(HttpServletRequest request) {
        TenantApiKey key = (TenantApiKey) request.getAttribute(
                com.gkht.ai.nexai.module.ai.apikey.infrastructure.gateway.ApiKeyAuthFilter.ATTRIBUTE_AUTHENTICATED_KEY);
        if (key == null) {
            throw exception(API_KEY_UNAUTHORIZED);
        }
        return key;
    }

    /**
     * OPENAI_CHUNK → SSE data 帧 + 末尾 [DONE]（OpenAI 流式协议）；
     * SESSION_ERROR 事件（装配失败等）转 OpenAI 错误体首帧后收尾。
     */
    private SseEmitter bridge(Flux<RuntimeEvent> events) {
        SseEmitter emitter = new SseEmitter(0L); // 无超时
        events.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> sendChunk(emitter, event),
                        error -> {
                            sendErrorChunk(emitter, error.getMessage());
                            emitter.complete();
                        },
                        () -> {
                            sendRaw(emitter, DONE_MARKER);
                            emitter.complete();
                        });
        return emitter;
    }

    private static void sendChunk(SseEmitter emitter, RuntimeEvent event) {
        if (event.type() == RuntimeEventType.OPENAI_CHUNK) {
            sendRaw(emitter, event.payload());
        } else if (event.type() == RuntimeEventType.SESSION_ERROR) {
            // 装配期错误（规格未发布/模型停用等）转 OpenAI 错误体
            String message = String.valueOf(JsonUtils.parseTree(event.payload())
                    .path("message").asText("服务暂不可用"));
            sendErrorChunk(emitter, message);
        }
        // 其余事件类型不出现在出口流（adapter 只产 chunk），忽略防御
    }

    private static void sendErrorChunk(SseEmitter emitter, String message) {
        sendRaw(emitter, JsonUtils.toJsonString(Map.of("error", Map.of(
                "message", message == null ? "服务暂不可用" : message,
                "type", "invalid_request_error"))));
    }

    private static void sendRaw(SseEmitter emitter, String data) {
        try {
            // 显式 JSON 媒体类型：SSE 帧以 UTF-8 编码（默认媒体类型会把非 ASCII 写成问号）
            emitter.send(SseEmitter.event().data(data, SSE_FRAME_MEDIA_TYPE));
        } catch (Exception ex) {
            // 客户端断开等 IO 失败：放弃后续帧（emitter 已不可用）
            emitter.completeWithError(ex);
        }
    }

}
