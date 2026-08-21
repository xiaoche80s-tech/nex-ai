package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ModelRepository;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.domain.repository.SessionRepository;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.infrastructure.converter.SessionConverter;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_MODEL_UNAVAILABLE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_SPEC_NOT_PUBLISHED;

/**
 * 会话应用服务实现。创建与装配读取走跨聚合只读查询（直接查对方 Repository）；
 * 发消息的同步段完成全部阻塞 DB 访问后，把冷的事件流交还调用方。
 */
@Service
@Validated
public class SessionServiceImpl implements SessionService {

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
        // 装配链的四次只读查询全部在调用线程完成，事件流回调中不再触碰 DB
        AgentSpecVersion version = requireVersion(session);
        AgentSpecConfigParts parts = resolveRuntimeModel(version);
        AgentSpecConfig snapshot = version.getConfig();
        AgentRuntimeConfig config = AgentRuntimeConfig.of(session.getSessionKey(),
                runtimeUserId(userId), agentName(session), snapshot.getSystemPrompt(),
                snapshot.getMaxIters(), snapshot.getTemperature(),
                parts.channel(), parts.model());
        session.recordMessageRound();
        sessionRepository.save(session);
        return agentRuntimeGateway.chat(config, command.getContent());
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
     * agent 名用「规格 + 版本」的稳定标识而非规格显示名（agentscope 用它做追踪与日志，
     * 规格名可改且可能含空格/特殊字符，不适合作为标识）
     */
    private String agentName(Session session) {
        return "spec-" + session.getSpecId() + "-v" + session.getVersionNo();
    }

    /**
     * 运行时用户标识：生产恒有登录态；无登录上下文时（如直连服务层测试）落到匿名槽位，
     * 同一会话的寻址键（userId, sessionKey）保持稳定即可
     */
    private String runtimeUserId(Long userId) {
        return userId != null ? String.valueOf(userId) : ANONYMOUS_USER_ID;
    }

    /** 装配链解析结果（channel + model 一起传递） */
    private record AgentSpecConfigParts(Channel channel, Model model) {
    }

}
