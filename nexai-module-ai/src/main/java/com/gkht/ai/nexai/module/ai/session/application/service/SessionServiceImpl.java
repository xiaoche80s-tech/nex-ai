package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionConfirmCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.PendingConfirmationDTO;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.domain.model.SessionStatus;
import com.gkht.ai.nexai.module.ai.session.domain.model.SessionType;
import com.gkht.ai.nexai.module.ai.session.domain.repository.SessionRepository;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import com.gkht.ai.nexai.module.ai.session.infrastructure.converter.SessionConverter;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import com.gkht.ai.nexai.framework.common.util.json.JsonUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASSEMBLE_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASKING_CONFIRM_REQUIRED;

/**
 * 会话应用服务实现。编排跨聚合只读（规格/版本/渠道/模型）与运行时网关端口：
 * 装配指令组装（快路径/回退分流）、消息轮次落库、HITL 挂起上下文持久化与恢复。
 *
 * <p>装配分流（工单 08）：默认版本 + 无参数覆盖 → 常驻快路径（装配指令版本号取规格当前版本，
 * 命中缓存即复用常驻实例）；非默认版本 → 回退 per-spec 装配（specReference 不同，自然回落）。
 * 运行时网关的版本戳失效（渠道/模型更新时间变化重建）对应用层透明。</p>
 */
@Service
@Validated
public class SessionServiceImpl implements SessionService {

    @Resource
    private SessionRepository sessionRepository;

    @Resource
    private SessionMapper sessionMapper;

    @Resource
    private SessionConverter sessionConverter;

    @Resource
    private AgentSpecRepository agentSpecRepository;

    @Resource
    private AgentRuntimeAssembler runtimeAssembler;

    @Resource
    private AgentRuntimeGateway runtimeGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDebugSession(DebugSessionCreateCommand command, Long userId) {
        if (userId == null) {
            throw exception(SESSION_ASSEMBLE_INVALID, "调试会话必须由登录用户发起");
        }
        requireSpec(command.getSpecId());
        String sessionKey = "dbg-" + UUID.randomUUID();
        Session session = Session.createDebug(sessionKey, command.getTitle(), userId,
                command.getSpecId(), command.getVersionNo());
        return sessionRepository.save(session);
    }

    @Override
    public Flux<RuntimeEvent> sendDebugMessage(Long sessionId, DebugSessionMessageCommand command,
                                               Long userId) {
        Session session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.ASKING) {
            // 挂起中不允许发新消息：先审批再续行（恢复交互，工单 09）
            return Flux.error(exception(SESSION_ASKING_CONFIRM_REQUIRED));
        }
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        // 消息轮次落库（rounds JSON 由应用层维护）
        session.appendRound(appendRound(session.getRounds(), command.getContent()));
        sessionRepository.update(session);
        return runtimeGateway.chat(config, command.getContent())
                .doOnNext(event -> onRuntimeEvent(sessionId, event));
    }

    @Override
    public Flux<RuntimeEvent> confirmDebugCalls(Long sessionId,
                                                DebugSessionConfirmCommand command, Long userId) {
        Session session = requireSession(sessionId);
        if (session.getStatus() != SessionStatus.ASKING) {
            return Flux.just(RuntimeEvent.of(
                    com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType.SESSION_ERROR,
                    JsonUtils.toJsonString(java.util.Map.of("type", "SESSION_ERROR",
                            "message", "当前会话没有挂起的审批"))));
        }
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        List<ToolCallDecision> decisions = command.getDecisions().stream()
                .map(d -> ToolCallDecision.of(d.getToolCallId(), d.getToolName(),
                        Boolean.TRUE.equals(d.getApproved()), d.getArgumentsJson()))
                .toList();
        // 审批回传：不在调用前清除挂起上下文——若 agentscope 拒绝陈旧/无关 toolCallId，
        // 挂起上下文保留可重试（工单 09 注释：validateAndAcceptConfirmResults 校验 resume payload）。
        // 续行流正常发出 USER_CONFIRM_RESULT 后再清除（见 onRuntimeEvent）。
        return runtimeGateway.confirmToolCalls(config, decisions)
                .doOnNext(event -> onRuntimeEvent(sessionId, event));
    }

    /**
     * 事件流拦截：REQUIRE_USER_CONFIRM 时把挂起上下文（RequireUserConfirmEvent JSON）持久化到
     * 会话、状态置 ASKING（关页重开可从持久化状态续接审批，工单 09）；USER_CONFIRM_RESULT
     * 时清除挂起上下文（审批已被 agentscope 接受，续行进行中）。按需重载会话避免共享实例
     * 在并发流下的状态污染。其余事件不落库（前端经 SSE 自渲染）。
     */
    private void onRuntimeEvent(Long sessionId, RuntimeEvent event) {
        com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType type = event.type();
        if (type == com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType.REQUIRE_USER_CONFIRM) {
            Session fresh = requireSession(sessionId);
            fresh.markPendingConfirmations(event.payload());
            sessionRepository.update(fresh);
        } else if (type == com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType.USER_CONFIRM_RESULT) {
            Session fresh = requireSession(sessionId);
            if (fresh.hasPendingConfirmations()) {
                fresh.clearPendingConfirmations();
                sessionRepository.update(fresh);
            }
        }
    }

    /** 轮次 JSON 追加：[] → [{content, time}] → […] */
    private String appendRound(String rounds, String content) {
        java.util.List<java.util.Map<String, Object>> list = JsonUtils.parseObject(rounds,
                new tools.jackson.core.type.TypeReference<
                        java.util.List<java.util.Map<String, Object>>>() {});
        if (list == null) {
            list = new java.util.ArrayList<>();
        }
        list.add(java.util.Map.of("content", content, "time", LocalDateTime.now().toString()));
        return JsonUtils.toJsonString(list);
    }

    @Override
    public Boolean interruptDebugSession(Long sessionId, Long userId) {
        Session session = requireSession(sessionId);
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        return runtimeGateway.interrupt(config);
    }

    @Override
    public PageResult<SessionDTO> getSessionPage(SessionPageQuery query) {
        return sessionConverter.toDTOPageFromDO(sessionMapper.selectPage(query,
                query.getSpecId(), query.getStatus()));
    }

    @Override
    public SessionDTO getSession(Long id) {
        Session session = requireSession(id);
        SessionDTO dto = sessionConverter.toDTO(session);
        AgentSpec spec = agentSpecRepository.findById(session.getSpecId());
        if (spec != null) {
            dto.setSpecCode(spec.getSpecCode());
        }
        return dto;
    }

    @Override
    public List<PendingConfirmationDTO> getPendingConfirmations(Long sessionId, Long userId) {
        Session session = requireSession(sessionId);
        if (session.getStatus() != SessionStatus.ASKING || !session.hasPendingConfirmations()) {
            return List.of();
        }
        // 挂起的 payload 为 RequireUserConfirmEvent JSON（含 toolCalls 数组），
        // 按事件结构解析出工具调用列表 → 审批卡片 DTO（恢复渲染，工单 09）
        List<ToolCallJSON> toolCalls = JsonUtils.parseArray(
                session.getPendingConfirmations(), "toolCalls", ToolCallJSON.class);
        if (toolCalls == null) {
            return List.of();
        }
        return toolCalls.stream()
                .map(tc -> {
                    PendingConfirmationDTO dto = new PendingConfirmationDTO();
                    dto.setToolCallId(tc.getId());
                    dto.setToolName(tc.getName());
                    dto.setArgumentsJson(tc.getInput() == null ? null
                            : JsonUtils.toJsonString(tc.getInput()));
                    return dto;
                })
                .toList();
    }

    @Override
    public List<String> listSessionWorkspaceFiles(Long sessionId, String relativePath, Long userId) {
        Session session = requireSession(sessionId);
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        return runtimeGateway.listWorkspaceFiles(config, relativePath);
    }

    @Override
    public List<java.util.Map<String, Object>> loadSessionHistory(Long sessionId, Long userId) {
        Session session = requireSession(sessionId);
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        return runtimeGateway.loadSessionMessages(config);
    }

    @Override
    public String readSessionWorkspaceFile(Long sessionId, String relativePath, Long userId) {
        Session session = requireSession(sessionId);
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        return runtimeGateway.readWorkspaceFile(config, relativePath);
    }

    /** RequireUserConfirmEvent.toolCalls 数组元素（与 agentscope ToolUseBlock 序列化同构） */
    @lombok.Data
    private static class ToolCallJSON {
        private String id;
        private String name;
        private java.util.Map<String, Object> input;
    }

    // ------------------------------------------------------------------
    //  装配指令组装（快路径/回退分流）
    // ------------------------------------------------------------------

    /**
     * 会话 → 装配指令：读规格（当前版本或指定版本快照）后经共享组装器装配
     * （与 OpenAI 兼容出口同一装配链，工单 16）。快路径：指定版本为空 → 用规格当前版本
     * （常驻缓存命中复用）；非默认版本 → specReference 不同，自然回落 per-spec 装配。
     */
    private AgentRuntimeConfig assembleRuntime(Session session, Long userId) {
        AgentSpec spec = requireSpec(session.getSpecId());
        AgentSpecVersion version = resolveVersion(spec, session.getVersionNo());
        String runtimeUserId = userId == null ? AgentRuntimeAssembler.ANONYMOUS_USER
                : String.valueOf(userId);
        return runtimeAssembler.assemble(spec, version, runtimeUserId, session.getSessionKey());
    }

    /** 解析装配版本快照：会话指定版本优先，否则规格当前版本 */
    private AgentSpecVersion resolveVersion(AgentSpec spec, Integer requestedVersionNo) {
        if (requestedVersionNo != null) {
            List<AgentSpecVersion> versions = agentSpecRepository.listVersions(spec.getId());
            return versions.stream()
                    .filter(v -> v.getVersionNo().equals(requestedVersionNo))
                    .findFirst()
                    .orElseThrow(() -> exception(SESSION_ASSEMBLE_INVALID,
                            "版本 " + requestedVersionNo + " 不存在"));
        }
        if (!spec.hasPublishedVersion()) {
            throw exception(SESSION_ASSEMBLE_INVALID, "规格尚未发布版本");
        }
        List<AgentSpecVersion> versions = agentSpecRepository.listVersions(spec.getId());
        return versions.stream()
                .filter(v -> v.getVersionNo().equals(spec.getCurrentVersionNo()))
                .findFirst()
                .orElseThrow(() -> exception(SESSION_ASSEMBLE_INVALID, "当前版本快照缺失"));
    }

    private Session requireSession(Long id) {
        Session session = sessionRepository.findById(id);
        if (session == null) {
            throw exception(SESSION_NOT_EXISTS);
        }
        return session;
    }

    private AgentSpec requireSpec(Long specId) {
        AgentSpec spec = agentSpecRepository.findById(specId);
        if (spec == null) {
            throw exception(AGENT_SPEC_NOT_EXISTS);
        }
        return spec;
    }

}
