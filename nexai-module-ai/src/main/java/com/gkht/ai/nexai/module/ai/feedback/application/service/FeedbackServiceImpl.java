package com.gkht.ai.nexai.module.ai.feedback.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackCreateCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackTransitionCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.dto.FeedbackDTO;
import com.gkht.ai.nexai.module.ai.feedback.application.query.FeedbackPageQuery;
import com.gkht.ai.nexai.module.ai.feedback.domain.exception.FeedbackStatusTransitionException;
import com.gkht.ai.nexai.module.ai.feedback.domain.model.Feedback;
import com.gkht.ai.nexai.module.ai.feedback.domain.repository.FeedbackRepository;
import com.gkht.ai.nexai.module.ai.feedback.domain.valueobject.FeedbackStatus;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.converter.FeedbackConverter;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.dataobject.FeedbackDO;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.mapper.FeedbackMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.FEEDBACK_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.FEEDBACK_STATUS_TRANSITION_ILLEGAL;

/**
 * 问题反馈应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查。
 */
@Service
@Validated
public class FeedbackServiceImpl implements FeedbackService {

    @Resource
    private FeedbackRepository feedbackRepository;

    @Resource
    private FeedbackMapper feedbackMapper;

    @Resource
    private FeedbackConverter feedbackConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFeedback(FeedbackCreateCommand command, Long submitterId) {
        Feedback feedback = Feedback.create(command.getContent(), command.getScreenshotUrls(),
                command.getSessionId(), submitterId);
        return feedbackRepository.save(feedback);
    }

    @Override
    public PageResult<FeedbackDTO> getFeedbackPage(FeedbackPageQuery query) {
        PageResult<FeedbackDO> page =
                feedbackMapper.selectPage(query, query.getStatus(), query.getContent());
        return feedbackConverter.toDTOPage(page);
    }

    @Override
    public FeedbackDTO getFeedback(Long id) {
        Feedback feedback = feedbackRepository.findById(id);
        if (feedback == null) {
            throw exception(FEEDBACK_NOT_EXISTS);
        }
        return feedbackConverter.toDTOFromDomain(feedback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transitionFeedback(FeedbackTransitionCommand command) {
        Feedback feedback = feedbackRepository.findById(command.getId());
        if (feedback == null) {
            throw exception(FEEDBACK_NOT_EXISTS);
        }
        try {
            feedback.transitionTo(FeedbackStatus.of(command.getTargetStatus()));
        } catch (FeedbackStatusTransitionException ex) {
            throw exception(FEEDBACK_STATUS_TRANSITION_ILLEGAL);
        }
        feedbackRepository.save(feedback);
    }

}
