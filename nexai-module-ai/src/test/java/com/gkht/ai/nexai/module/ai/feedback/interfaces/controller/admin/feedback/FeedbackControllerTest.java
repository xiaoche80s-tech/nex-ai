package com.gkht.ai.nexai.module.ai.feedback.interfaces.controller.admin.feedback;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.security.core.LoginUser;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackCreateCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackTransitionCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.dto.FeedbackDTO;
import com.gkht.ai.nexai.module.ai.feedback.application.query.FeedbackPageQuery;
import com.gkht.ai.nexai.module.ai.feedback.application.service.FeedbackServiceImpl;
import com.gkht.ai.nexai.module.ai.feedback.domain.valueobject.FeedbackStatus;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.converter.FeedbackConverterImpl;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.dataobject.FeedbackDO;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.mapper.FeedbackMapper;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.repository.FeedbackRepositoryImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.enums.GlobalErrorCodeConstants.SUCCESS;
import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.FEEDBACK_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.FEEDBACK_STATUS_TRANSITION_ILLEGAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 问题反馈 S1 接缝测试：直接调用 /admin-api/ai/feedback/** 对应的控制器方法，
 * 走真实 controller → application → repository → H2 全链路，只断言外部行为。
 */
@Import({FeedbackController.class, FeedbackServiceImpl.class,
        FeedbackRepositoryImpl.class, FeedbackConverterImpl.class})
public class FeedbackControllerTest extends BaseDbAndRedisUnitTest {

    private static final Long SUBMITTER_ID = 1L;

    @Resource
    private FeedbackController feedbackController;

    @Resource
    private FeedbackMapper feedbackMapper;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(1L);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(SUBMITTER_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(loginUser, null, "mock"));
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private FeedbackDO insertFeedback(Integer status) {
        FeedbackDO feedbackDO = new FeedbackDO();
        feedbackDO.setContent("调试台回复出现乱码");
        feedbackDO.setScreenshotUrls(List.of("http://127.0.0.1:48080/admin-api/infra/file/get/f1.png"));
        feedbackDO.setSessionId("debug-session-001");
        feedbackDO.setStatus(status);
        feedbackDO.setSubmitterId(SUBMITTER_ID);
        feedbackMapper.insert(feedbackDO);
        return feedbackDO;
    }

    @Test
    @DisplayName("提交反馈：落库为待处理，截图 URL 列表序列化保存，提交人取自登录态")
    public void createFeedbackSuccess() {
        FeedbackCreateCommand command = new FeedbackCreateCommand();
        command.setContent("模型回复与预期不符");
        command.setScreenshotUrls(List.of("http://f1.png", "http://f2.png"));
        command.setSessionId("debug-session-002");

        Long feedbackId = feedbackController.createFeedback(command).getData();

        assertNotNull(feedbackId);
        FeedbackDO inserted = feedbackMapper.selectById(feedbackId);
        assertEquals("模型回复与预期不符", inserted.getContent());
        assertEquals(List.of("http://f1.png", "http://f2.png"), inserted.getScreenshotUrls());
        assertEquals("debug-session-002", inserted.getSessionId());
        assertEquals(FeedbackStatus.PENDING.getCode(), inserted.getStatus());
        assertEquals(SUBMITTER_ID, inserted.getSubmitterId());
    }

    @Test
    @DisplayName("提交反馈：无截图与会话时可空提交")
    public void createFeedbackWithoutOptionalFields() {
        FeedbackCreateCommand command = new FeedbackCreateCommand();
        command.setContent("页面加载缓慢");

        Long feedbackId = feedbackController.createFeedback(command).getData();

        FeedbackDO inserted = feedbackMapper.selectById(feedbackId);
        assertEquals(List.of(), inserted.getScreenshotUrls());
        assertNull(inserted.getSessionId());
    }

    @Test
    @DisplayName("分页查询：按状态过滤，DTO 携带截图 URL 列表")
    public void getFeedbackPageByStatus() {
        insertFeedback(FeedbackStatus.PENDING.getCode());
        FeedbackDO processing = insertFeedback(FeedbackStatus.PROCESSING.getCode());
        insertFeedback(FeedbackStatus.CLOSED.getCode());

        FeedbackPageQuery query = new FeedbackPageQuery();
        query.setStatus(FeedbackStatus.PROCESSING.getCode());

        PageResult<FeedbackDTO> page = feedbackController.getFeedbackPage(query).getData();

        assertEquals(1, page.getTotal());
        assertEquals(processing.getId(), page.getList().get(0).getId());
        assertEquals("调试台回复出现乱码", page.getList().get(0).getContent());
        assertEquals(List.of("http://127.0.0.1:48080/admin-api/infra/file/get/f1.png"),
                page.getList().get(0).getScreenshotUrls());
        assertEquals(FeedbackStatus.PROCESSING.getCode(), page.getList().get(0).getStatus());
    }

    @Test
    @DisplayName("分页查询：无条件时返回全部")
    public void getFeedbackPageWithoutCondition() {
        insertFeedback(FeedbackStatus.PENDING.getCode());
        insertFeedback(FeedbackStatus.PROCESSING.getCode());

        PageResult<FeedbackDTO> page = feedbackController.getFeedbackPage(new FeedbackPageQuery()).getData();

        assertEquals(2, page.getTotal());
    }

    @Test
    @DisplayName("详情：返回完整字段")
    public void getFeedbackDetail() {
        FeedbackDO feedbackDO = insertFeedback(FeedbackStatus.PENDING.getCode());

        FeedbackDTO dto = feedbackController.getFeedback(feedbackDO.getId()).getData();

        assertEquals(feedbackDO.getId(), dto.getId());
        assertEquals(feedbackDO.getContent(), dto.getContent());
        assertEquals(feedbackDO.getScreenshotUrls(), dto.getScreenshotUrls());
        assertEquals(feedbackDO.getSessionId(), dto.getSessionId());
        assertEquals(FeedbackStatus.PENDING.getCode(), dto.getStatus());
        assertEquals(SUBMITTER_ID, dto.getSubmitterId());
        assertNotNull(dto.getCreateTime());
    }

    @Test
    @DisplayName("详情：不存在时报 FEEDBACK_NOT_EXISTS")
    public void getFeedbackNotExists() {
        assertServiceException(() -> feedbackController.getFeedback(999L), FEEDBACK_NOT_EXISTS);
    }

    @Test
    @DisplayName("状态流转：待处理 → 处理中 → 已解决 → 已关闭 全链路合法")
    public void transitionLegalChain() {
        FeedbackDO feedbackDO = insertFeedback(FeedbackStatus.PENDING.getCode());

        transitionAndAssert(feedbackDO.getId(), FeedbackStatus.PROCESSING);
        transitionAndAssert(feedbackDO.getId(), FeedbackStatus.RESOLVED);
        transitionAndAssert(feedbackDO.getId(), FeedbackStatus.CLOSED);
    }

    private void transitionAndAssert(Long feedbackId, FeedbackStatus target) {
        FeedbackTransitionCommand command = new FeedbackTransitionCommand();
        command.setId(feedbackId);
        command.setTargetStatus(target.getCode());

        assertEquals(SUCCESS.getCode(), feedbackController.transitionFeedback(command).getCode());

        assertEquals(target.getCode(), feedbackMapper.selectById(feedbackId).getStatus());
    }

    @Test
    @DisplayName("状态流转：已关闭为终态，再流转报 FEEDBACK_STATUS_TRANSITION_ILLEGAL")
    public void transitionFromClosedIllegal() {
        FeedbackDO feedbackDO = insertFeedback(FeedbackStatus.CLOSED.getCode());

        FeedbackTransitionCommand command = new FeedbackTransitionCommand();
        command.setId(feedbackDO.getId());
        command.setTargetStatus(FeedbackStatus.PROCESSING.getCode());

        assertServiceException(() -> feedbackController.transitionFeedback(command),
                FEEDBACK_STATUS_TRANSITION_ILLEGAL);
        assertEquals(FeedbackStatus.CLOSED.getCode(),
                feedbackMapper.selectById(feedbackDO.getId()).getStatus());
    }

    @Test
    @DisplayName("状态流转：未知状态编码同样报流转不合法")
    public void transitionUnknownTargetStatus() {
        FeedbackDO feedbackDO = insertFeedback(FeedbackStatus.PENDING.getCode());

        FeedbackTransitionCommand command = new FeedbackTransitionCommand();
        command.setId(feedbackDO.getId());
        command.setTargetStatus(99);

        assertServiceException(() -> feedbackController.transitionFeedback(command),
                FEEDBACK_STATUS_TRANSITION_ILLEGAL);
    }

    @Test
    @DisplayName("状态流转：反馈不存在时报 FEEDBACK_NOT_EXISTS")
    public void transitionNotExists() {
        FeedbackTransitionCommand command = new FeedbackTransitionCommand();
        command.setId(999L);
        command.setTargetStatus(FeedbackStatus.PROCESSING.getCode());

        assertServiceException(() -> feedbackController.transitionFeedback(command), FEEDBACK_NOT_EXISTS);
    }

}
