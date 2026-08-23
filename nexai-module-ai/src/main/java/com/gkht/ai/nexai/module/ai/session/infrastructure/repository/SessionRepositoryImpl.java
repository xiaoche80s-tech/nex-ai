package com.gkht.ai.nexai.module.ai.session.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.domain.model.SessionStatus;
import com.gkht.ai.nexai.module.ai.session.domain.model.SessionType;
import com.gkht.ai.nexai.module.ai.session.domain.repository.SessionRepository;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话 Repository 实现：SessionDO ↔ 领域模型适配。
 * 聚合重建（reconstitute）与轮次/挂起上下文的更新随本工单启用。
 */
@Repository
public class SessionRepositoryImpl implements SessionRepository {

    @Resource
    private SessionMapper sessionMapper;

    @Override
    public Long save(Session session) {
        SessionDO dataObject = toDataObject(session);
        sessionMapper.insert(dataObject);
        return dataObject.getId();
    }

    @Override
    public Session findById(Long id) {
        SessionDO dataObject = sessionMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public Session findBySessionKey(String sessionKey) {
        SessionDO dataObject = sessionMapper.selectBySessionKey(sessionKey);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    @Override
    public void update(Session session) {
        sessionMapper.updateById(toDataObject(session));
    }

    @Override
    public List<Session> listByTenant(SessionType type, Long specId, int limit) {
        return sessionMapper.selectListByTenant(type.name(), specId, limit).stream()
                .map(this::reconstitute).toList();
    }

    private SessionDO toDataObject(Session session) {
        SessionDO dataObject = new SessionDO();
        dataObject.setId(session.getId());
        dataObject.setSessionKey(session.getSessionKey());
        dataObject.setTitle(session.getTitle());
        dataObject.setType(session.getType().name());
        dataObject.setUserId(session.getUserId());
        dataObject.setSpecId(session.getSpecId());
        dataObject.setVersionNo(session.getVersionNo());
        dataObject.setStatus(session.getStatus().name());
        dataObject.setRounds(session.getRounds());
        dataObject.setPendingConfirmations(session.getPendingConfirmations());
        return dataObject;
    }

    private Session reconstitute(SessionDO dataObject) {
        return Session.reconstitute(dataObject.getId(), dataObject.getSessionKey(),
                dataObject.getTitle(), SessionType.valueOf(dataObject.getType()),
                dataObject.getUserId(), dataObject.getSpecId(), dataObject.getVersionNo(),
                SessionStatus.valueOf(dataObject.getStatus()), dataObject.getRounds(),
                dataObject.getPendingConfirmations(), dataObject.getCreateTime());
    }

}
