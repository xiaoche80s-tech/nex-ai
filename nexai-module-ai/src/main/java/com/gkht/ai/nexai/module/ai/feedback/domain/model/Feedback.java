package com.gkht.ai.nexai.module.ai.feedback.domain.model;

import com.gkht.ai.nexai.module.ai.feedback.domain.exception.FeedbackStatusTransitionException;
import com.gkht.ai.nexai.module.ai.feedback.domain.valueobject.FeedbackStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问题反馈聚合根（充血模型，零框架依赖）。
 *
 * <p>由登录用户提交，可携带截图 URL 列表与可选的关联会话标识；
 * 提交后进入 {@link FeedbackStatus#PENDING}，由管理员按流转规则推进处理状态。</p>
 */
public class Feedback {

    /** 编号，未落库时为 null */
    private Long id;
    /** 反馈内容 */
    private String content;
    /** 截图 URL 列表（文件本体经 infra 文件服务上传，此处只持引用） */
    private List<String> screenshotUrls;
    /** 关联会话标识，可空（M1 调试台提交反馈时携带） */
    private String sessionId;
    /** 处理状态 */
    private FeedbackStatus status;
    /** 提交人用户编号 */
    private Long submitterId;
    /** 提交时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private Feedback(Long id, String content, List<String> screenshotUrls,
                     String sessionId, FeedbackStatus status, Long submitterId, LocalDateTime createTime) {
        this.id = id;
        this.content = content;
        this.screenshotUrls = screenshotUrls;
        this.sessionId = sessionId;
        this.status = status;
        this.submitterId = submitterId;
        this.createTime = createTime;
    }

    /**
     * 提交一条新反馈，初始状态为待处理
     *
     * @param content        反馈内容，不能为空白
     * @param screenshotUrls 截图 URL 列表，可空
     * @param sessionId      关联会话标识，可空
     * @param submitterId    提交人用户编号
     */
    public static Feedback create(String content, List<String> screenshotUrls,
                                  String sessionId, Long submitterId) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }
        return new Feedback(null, content, snapshotUrls(screenshotUrls), sessionId,
                FeedbackStatus.PENDING, submitterId, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用）
     */
    public static Feedback reconstitute(Long id, String content, List<String> screenshotUrls,
                                        String sessionId, FeedbackStatus status, Long submitterId,
                                        LocalDateTime createTime) {
        return new Feedback(id, content, snapshotUrls(screenshotUrls), sessionId, status, submitterId, createTime);
    }

    private static List<String> snapshotUrls(List<String> screenshotUrls) {
        return screenshotUrls == null ? List.of() : List.copyOf(screenshotUrls);
    }

    /**
     * 流转处理状态，非法流转抛出 {@link FeedbackStatusTransitionException}
     */
    public void transitionTo(FeedbackStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new FeedbackStatusTransitionException(status, target);
        }
        this.status = target;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public List<String> getScreenshotUrls() {
        return screenshotUrls;
    }

    public String getSessionId() {
        return sessionId;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public Long getSubmitterId() {
        return submitterId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

}
