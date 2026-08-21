package com.gkht.ai.nexai.module.ai.feedback.interfaces.controller.admin.feedback;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.security.core.util.SecurityFrameworkUtils;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackCreateCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.command.FeedbackTransitionCommand;
import com.gkht.ai.nexai.module.ai.feedback.application.dto.FeedbackDTO;
import com.gkht.ai.nexai.module.ai.feedback.application.query.FeedbackPageQuery;
import com.gkht.ai.nexai.module.ai.feedback.application.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 问题反馈")
@RestController
@RequestMapping("/ai/feedback")
@Validated
public class FeedbackController {

    @Resource
    private FeedbackService feedbackService;

    @PostMapping("/create")
    @Operation(summary = "提交问题反馈")
    @PreAuthorize("@ss.hasPermission('ai:feedback:create')")
    public CommonResult<Long> createFeedback(@Valid @RequestBody FeedbackCreateCommand command) {
        // 提交人从登录态取，不信任请求体，防伪造
        return success(feedbackService.createFeedback(command, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得问题反馈分页")
    @PreAuthorize("@ss.hasPermission('ai:feedback:query')")
    public CommonResult<PageResult<FeedbackDTO>> getFeedbackPage(@Validated FeedbackPageQuery query) {
        return success(feedbackService.getFeedbackPage(query));
    }

    @GetMapping("/get")
    @Operation(summary = "获得问题反馈详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:feedback:query')")
    public CommonResult<FeedbackDTO> getFeedback(@RequestParam("id") Long id) {
        return success(feedbackService.getFeedback(id));
    }

    @PutMapping("/transition")
    @Operation(summary = "流转问题反馈处理状态")
    @PreAuthorize("@ss.hasPermission('ai:feedback:update')")
    public CommonResult<Boolean> transitionFeedback(@Valid @RequestBody FeedbackTransitionCommand command) {
        feedbackService.transitionFeedback(command);
        return success(true);
    }

}
