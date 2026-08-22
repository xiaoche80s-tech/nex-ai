package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ModelRepository;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCloneCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionConfirmCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.domain.exception.SessionRunningException;
import com.gkht.ai.nexai.module.ai.session.domain.exception.SessionSandboxUnavailableException;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.domain.repository.SessionRepository;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import com.gkht.ai.nexai.module.ai.session.infrastructure.converter.SessionConverter;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_MODEL_UNAVAILABLE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_RUNNING;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_SANDBOX_UNAVAILABLE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_SPEC_NOT_PUBLISHED;

/**
 * 会话应用服务实现。创建与装配读取走跨聚合只读查询（直接查对方 Repository）；
 * 发消息/确认回应的同步段完成全部阻塞 DB 访问后，把冷的事件流交还调用方。
 */
@Service
@Validated
public class SessionServiceImpl implements SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionServiceImpl.class);

    /** 无登录上下文时的运行时用户槽位（测试直连服务层场景；生产恒有登录态） */
    public static final String ANONYMOUS_USER_ID = "anonymous";

    @Resource
    private SessionRepository sessionRepository;

    @Resource
    private SessionMapper sessionMapper;

    @Resource
    private SessionConverter sessionConverter;

    @Resource
    private AgentSpecRepository agentSpecRepository;

    @Resource
    private ModelRepository modelRepository;

    @Resource
    private ChannelRepository channelRepository;

    @Resource
    private AgentRuntimeGateway agentRuntimeGateway;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDebugSession(DebugSessionCreateCommand command) {
        AgentSpec spec = agentSpecRepository.findById(command.getSpecId());
        if (spec == null) {
            throw exception(AGENT_SPEC_NOT_EXISTS);
        }
        Integer versionNo = command.getVersionNo() != null ? command.getVersionNo() : spec.getCurrentVersionNo();
        if (versionNo == null) {
            throw exception(SESSION_SPEC_NOT_PUBLISHED);
        }
        // 显式指定版本必须真实存在；默认版本由发布语义保证存在，同样校验兜底逻辑删除场景
        AgentSpecVersion version = agentSpecRepository.findVersion(spec.getId(), versionNo);
        if (version == null) {
            throw exception(AGENT_SPEC_VERSION_NOT_EXISTS);
        }
        Session session = Session.startDebug(spec.getId(), versionNo, command.getTitle());
        return sessionRepository.save(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flux<String> sendDebugMessage(Long sessionId, DebugSessionMessageCommand command, Long userId) {
        Session session = requireSession(sessionId);
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        session.recordMessageRound();
        sessionRepository.save(session);
        return startStream(() -> agentRuntimeGateway.chat(config, command.getContent()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Flux<String> confirmToolCalls(Long sessionId, DebugSessionConfirmCommand command, Long userId) {
        Session session = requireSession(sessionId);
        AgentRuntimeConfig config = assembleRuntime(session, userId);
        List<ToolCallDecision> decisions = command.getDecisions().stream()
                .map(item -> new ToolCallDecision(item.getToolCallId(), item.getToolName(),
                        item.getArguments(), item.isApproved()))
                .toList();
        // 确认回应是挂起轮的延续而非新用户消息，不计入消息轮数
        return startStream(() -> agentRuntimeGateway.confirmToolCalls(config, decisions));
    }

    /**
     * 发起事件流的共用入口：网关在同步段发现同一会话已有运行中的流时抛
     * {@link SessionRunningException}、环境无 docker 而规格要求沙箱时抛
     * {@link SessionSandboxUnavailableException}，分别转业务错误
     * （此时轮次等变更随事务一并回滚）
     */
    private Flux<String> startStream(java.util.function.Supplier<Flux<String>> stream) {
        try {
            return stream.get();
        } catch (SessionRunningException ex) {
            throw exception(SESSION_RUNNING);
        } catch (SessionSandboxUnavailableException ex) {
            log.warn("[startStream][{}]", ex.getMessage());
            throw exception(SESSION_SANDBOX_UNAVAILABLE);
        }
    }

    @Override
    public boolean interruptSession(Long sessionId) {
        Session session = requireSession(sessionId);
        return agentRuntimeGateway.interrupt(session.getSessionKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long cloneDebugSession(Long sourceId, DebugSessionCloneCommand command, Long userId) {
        Session source = requireSession(sourceId);
        Session cloned = source.cloneAsDebug(command.getTitle(),
                command.getMaxIters(), command.getTemperature());
        Long clonedId = sessionRepository.save(cloned);
        // 对话历史随克隆复制（同一状态存储内搬移）；源无状态或复制失败降级为空白新会话，不阻断克隆
        try {
            agentRuntimeGateway.copySessionState(stateUserId(source), source.getSessionKey(),
                    runtimeUserId(userId, requireSpecForRuntime(source)), cloned.getSessionKey());
        } catch (Exception ex) {
            log.warn("[cloneDebugSession][源会话 {} 状态复制到 {} 失败，降级为空白克隆：{}]",
                    source.getId(), cloned.getSessionKey(), ex.getMessage());
        }
        return clonedId;
    }

    @Override
    public PageResult<SessionDTO> getSessionPage(SessionPageQuery query) {
        PageResult<SessionDO> page = sessionMapper.selectPage(query, query.getType(),
                query.getSpecId(), query.getVersionNo());
        return sessionConverter.toDTOPage(page);
    }

    private Session requireSession(Long id) {
        Session session = sessionRepository.findById(id);
        if (session == null) {
            throw exception(SESSION_NOT_EXISTS);
        }
        return session;
    }

    /**
     * 发起事件流前的共用装配链：会话绑定的版本快照 + 规格归属（spec_code/层级，workspace 布局与
     * agentName 用）+ 渠道模型解析 + 会话级推理参数覆盖 + 执行环境透传。
     * 覆盖优先于快照（克隆重跑微调参数的生效点；调试台目前仅支持温度覆盖，
     * topP/maxTokens 恒取快照值）。运行中重复发起由网关抛
     * {@link SessionRunningException}，此处统一转业务错误。
     */
    private AgentRuntimeConfig assembleRuntime(Session session, Long userId) {
        AgentSpec spec = requireSpecForRuntime(session);
        AgentSpecVersion version = requireVersion(session);
        AgentSpecConfigParts parts = resolveRuntimeModel(version);
        AgentSpecConfig snapshot = version.getConfig();
        Integer maxIters = session.getOverrideMaxIters() != null
                ? session.getOverrideMaxIters() : snapshot.getMaxIters();
        GenerateOptions snapshotOptions = snapshot.getGenerateOptions();
        GenerateOptions effectiveOptions = GenerateOptions.of(
                session.getOverrideTemperature() != null ? session.getOverrideTemperature()
                        : (snapshotOptions == null ? null : snapshotOptions.getTemperature()),
                snapshotOptions == null ? null : snapshotOptions.getTopP(),
                snapshotOptions == null ? null : snapshotOptions.getMaxTokens());
        return AgentRuntimeConfig.of(session.getSessionKey(),
                runtimeUserId(userId, spec), agentName(spec, session.getVersionNo()),
                spec.getSpecCode(), spec.getOwnerLevel(), spec.getOwnerUserId(),
                snapshot.getSystemPrompt(), maxIters, effectiveOptions,
                snapshot.getExecutionEnv(),
                parts.channel(), parts.model());
    }

    /** 会话绑定的规格必须仍可读取（逻辑删除兜底） */
    private AgentSpec requireSpecForRuntime(Session session) {
        AgentSpec spec = agentSpecRepository.findById(session.getSpecId());
        if (spec == null) {
            throw exception(AGENT_SPEC_NOT_EXISTS);
        }
        return spec;
    }

    /**
     * 会话绑定的版本快照必须仍可读取（版本被级联删除时给出明确错误而非装配失败）
     */
    private AgentSpecVersion requireVersion(Session session) {
        AgentSpecVersion version = agentSpecRepository.findVersion(session.getSpecId(), session.getVersionNo());
        if (version == null) {
            throw exception(AGENT_SPEC_VERSION_NOT_EXISTS);
        }
        return version;
    }

    /**
     * 解析快照引用的模型与渠道：存在且均启用才可装配（工单 05 约定停用模型由运行时兜底拦截）
     */
    private AgentSpecConfigParts resolveRuntimeModel(AgentSpecVersion version) {
        Long modelId = version.getConfig().getModelId();
        Model model = modelRepository.findById(modelId);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
        Channel channel = channelRepository.findById(model.getChannelId());
        if (channel == null) {
            throw exception(CHANNEL_NOT_EXISTS);
        }
        if (!model.isEnabled() || !channel.isEnabled()) {
            throw exception(SESSION_MODEL_UNAVAILABLE);
        }
        return new AgentSpecConfigParts(channel, model);
    }

    /**
     * agent 名 = {spec_code}-v{versionNo}（agentscope 用它做追踪与日志；spec_code 创建后
     * 不可变、字符集受控，比规格显示名（可改、可含特殊字符）与主键（外溢敏感）都合适）
     */
    private String agentName(AgentSpec spec, Integer versionNo) {
        return spec.getSpecCode() + "-v" + versionNo;
    }

    /**
     * 运行时用户标识（agentscope 状态存储 userId 槽位与 USER scope namespace 前缀）：
     * 生产恒有登录态；无登录上下文时（如直连服务层测试）落到匿名槽位，同一会话的寻址键
     * （userId, sessionKey）保持稳定即可。平台级规格跨租户复复合 id（t{tenant}-u{user}），
     * 使记忆/用户数据天然按租户+用户分桶（M2+ 平台级开放时生效）。
     */
    private String runtimeUserId(Long userId, AgentSpec spec) {
        if (userId == null) {
            return ANONYMOUS_USER_ID;
        }
        if (spec.getOwnerLevel() == OwnerLevel.PLATFORM) {
            return "t" + TenantContextHolder.getRequiredTenantId() + "-u" + userId;
        }
        return String.valueOf(userId);
    }

    /**
     * 源会话状态所在的 userId 槽位：会话创建者的持久化痕迹（creator 列），
     * 无记录时（测试直连、历史数据）退化为匿名槽位
     */
    private String stateUserId(Session session) {
        String creator = session.getCreatorUserId();
        return creator == null || creator.isBlank() ? ANONYMOUS_USER_ID : creator;
    }

    /** 装配链解析结果（channel + model 一起传递） */
    private record AgentSpecConfigParts(Channel channel, Model model) {
    }

}
