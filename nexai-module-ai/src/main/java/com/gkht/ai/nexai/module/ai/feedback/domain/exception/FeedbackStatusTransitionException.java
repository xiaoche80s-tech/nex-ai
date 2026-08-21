package com.gkht.ai.nexai.module.ai.feedback.domain.exception;

import com.gkht.ai.nexai.module.ai.feedback.domain.valueobject.FeedbackStatus;

/**
 * 问题反馈状态流转不合法（如从终态「已关闭」流转、流转到自身或空目标）。
 * 由 application 层捕获并转为带错误码的 ServiceException。
 */
public class FeedbackStatusTransitionException extends RuntimeException {

    public FeedbackStatusTransitionException(FeedbackStatus from, FeedbackStatus target) {
        super(String.format("问题反馈状态不支持从「%s」流转到「%s」",
                from.getLabel(), target == null ? "空值" : target.getLabel()));
    }

}
