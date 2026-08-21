package com.gkht.ai.nexai.module.ai.feedback.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackCreateCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackTransitionCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.dto.FeedbackDTO;
import com.gkht.ai.nexai.module.ai.feedback.application.query.FeedbackPageQuery;

/**
 * 问题反馈应用服务：只做编排，业务规则在 Feedback 聚合根与 FeedbackStatus 值对象。
 */
public interface FeedbackService {

    /**
     * 提交问题反馈，返回反馈编号。submitterId 由入口层从登录态取出来防伪造。
     */
    Long createFeedback(FeedbackCreateCommand command, Long submitterId);

    /**
     * 分页查询问题反馈（读侧经 Mapper 直查转 DTO）
     */
    PageResult<FeedbackDTO> getFeedbackPage(FeedbackPageQuery query);

    /**
     * 查询问题反馈详情，不存在抛 FEEDBACK_NOT_EXISTS
     */
    FeedbackDTO getFeedback(Long id);

    /**
     * 流转处理状态，非法流转抛 FEEDBACK_STATUS_TRANSITION_ILLEGAL
     */
    void transitionFeedback(FeedbackTransitionCommand command);

}
