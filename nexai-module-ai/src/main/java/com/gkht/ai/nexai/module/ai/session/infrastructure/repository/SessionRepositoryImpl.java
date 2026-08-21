package com.gkht.ai.nexai.module.ai.session.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.domain.repository.SessionRepository;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.SessionType;
import com.gkht.ai.nexai.module.ai.session.infrastructure.converter.SessionConverter;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 会话 Repository 实现：DO ↔ 领域模型适配，聚合重建经 Session.reconstitute。
 */
@Repository
public class SessionRepositoryImpl implements SessionRepository {

    @Resource
    private SessionMapper sessionMapper;

    @Resource
    private SessionConverter sessionConverter;

    @Override
    public Long save(Session session) {
        SessionDO dataObject = sessionConverter.toDataObject(session);
        if (dataObject.getId() == null) {
            sessionMapper.insert(dataObject);
        } else {
            sessionMapper.updateById(dataObject);
        }
        return dataObject.getId();
    }

    @Override
    public Session findById(Long id) {
        SessionDO dataObject = sessionMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    private Session reconstitute(SessionDO dataObject) {
        return Session.reconstitute(dataObject.getId(), dataObject.getSessionKey(),
                SessionType.of(dataObject.getType()), dataObject.getSpecId(),
                dataObject.getVersionNo(), dataObject.getTitle(), dataObject.getMessageRounds(),
                dataObject.getCreateTime());
    }

}
