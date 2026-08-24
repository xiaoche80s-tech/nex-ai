package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.module.ai.agentspec.application.dto.EffectiveSpecSnapshot;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASSEMBLE_INVALID;

/**
 * OpenAI 兼容出口应用服务实现（工单 16）：model 字段（= specCode）路由 → 读规格当前版本
 * → 经共享装配器组指令（与调试会话同一装配链：挂载翻译/审计/用量/版本戳失效一致）→
 * 网关端口流式调用。
 *
 * <p><b>无状态出口</b>：客户端管理全量历史，每次调用独立会话槽位
 * （{@code oa-{requestId}}，槽位残留治理后置）；实例常驻复用不 per-请求新建。
 * 用户级规格不对外出口（终端调用面向租户级智能体——API Key 为租户凭证）。</p>
 */
@Service
public class OpenAiCompatServiceImpl implements OpenAiCompatService {

    /** 出口会话槽位前缀（与调试会话 dbg- 区分；每次请求独立） */
    static final String OPENAI_SESSION_PREFIX = "oa-";
    /** 出口槽位用户（API Key 调用方非终端登录用户） */
    static final String OPENAI_USER = "openai-client";

    @Resource
    private AgentSpecService agentSpecService;

    @Resource
    private AgentRuntimeAssembler runtimeAssembler;

    @Resource
    private AgentRuntimeGateway runtimeGateway;

    @Override
    public Flux<RuntimeEvent> streamChatCompletions(String specCode, List<ChatMessageInput> messages,
                                                    String requestId) {
        try {
            // 生效快照单一入口解析（specCode 路由；不存在/未发布/快照缺失由入口报错）
            EffectiveSpecSnapshot snapshot = agentSpecService.resolveCurrentVersionByCode(specCode);
            AgentSpec spec = snapshot.spec();
            // 用户级规格不对外出口：API Key 为租户凭证，放行用户级规格等于跨用户越权
            if (spec.getOwnerLevel()
                    == com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel.USER) {
                return Flux.error(exception(SESSION_ASSEMBLE_INVALID, "用户级规格不对外部出口开放"));
            }
            // 出口调用方为 API Key（租户凭证），槽位用户固定标识；会话槽位按请求隔离
            AgentRuntimeConfig config = runtimeAssembler.assemble(spec, snapshot.version(),
                    OPENAI_USER, OPENAI_SESSION_PREFIX + effectiveRequestId(requestId));
            return runtimeGateway.chatOpenAi(config, messages,
                    effectiveRequestId(requestId));
        } catch (Exception ex) {
            return Flux.error(ex);
        }
    }

    /** 请求标识兜底（未传时生成） */
    private static String effectiveRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

}
