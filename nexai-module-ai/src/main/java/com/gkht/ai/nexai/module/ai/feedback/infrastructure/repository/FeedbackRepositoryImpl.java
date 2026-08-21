package com.gkht.ai.nexai.module.ai.feedback.infrastructure.repository;

import com.gkht.ai.nexai.module.ai.feedback.domain.model.Feedback;
import com.gkht.ai.nexai.module.ai.feedback.domain.repository.FeedbackRepository;
import com.gkht.ai.nexai.module.ai.feedback.domain.valueobject.FeedbackStatus;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.converter.FeedbackConverter;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.dataobject.FeedbackDO;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.mapper.FeedbackMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

/**
 * 问题反馈 Repository 实现：DO ↔ 领域模型适配，聚合重建经 Feedback.reconstitute。
 */
@Repository
public class FeedbackRepositoryImpl implements FeedbackRepository {

    @Resource
    private FeedbackMapper feedbackMapper;

    @Resource
    private FeedbackConverter feedbackConverter;

    @Override
    public Long save(Feedback feedback) {
        FeedbackDO dataObject = feedbackConverter.toDataObject(feedback);
        if (dataObject.getId() == null) {
            feedbackMapper.insert(dataObject);
        } else {
            feedbackMapper.updateById(dataObject);
        }
        return dataObject.getId();
    }

    @Override
    public Feedback findById(Long id) {
        FeedbackDO dataObject = feedbackMapper.selectById(id);
        return dataObject == null ? null : reconstitute(dataObject);
    }

    private Feedback reconstitute(FeedbackDO dataObject) {
        FeedbackStatus status = FeedbackStatus.of(dataObject.getStatus());
        if (status == null) {
            // 状态编码非法属于脏数据，直接失败暴露而非静默吞掉
            throw new IllegalStateException(String.format("问题反馈 #%s 的状态编码非法：%s",
                    dataObject.getId(), dataObject.getStatus()));
        }
        return Feedback.reconstitute(dataObject.getId(), dataObject.getContent(),
                dataObject.getScreenshotUrls(), dataObject.getSessionId(), status,
                dataObject.getSubmitterId(), dataObject.getCreateTime());
    }

}
