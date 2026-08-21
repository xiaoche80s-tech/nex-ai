package com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.model.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ChannelPageQuery;
import com.gkht.ai.nexai.module.ai.model.application.service.ChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 模型渠道")
@RestController
@RequestMapping("/ai/channel")
@Validated
public class ChannelController {

    @Resource
    private ChannelService channelService;

    @PostMapping("/create")
    @Operation(summary = "创建渠道")
    @PreAuthorize("@ss.hasPermission('ai:channel:create')")
    public CommonResult<Long> createChannel(@Valid @RequestBody ChannelCreateCommand command) {
        return success(channelService.createChannel(command));
    }

    @PutMapping("/update")
    @Operation(summary = "更新渠道")
    @PreAuthorize("@ss.hasPermission('ai:channel:update')")
    public CommonResult<Boolean> updateChannel(@Valid @RequestBody ChannelUpdateCommand command) {
        channelService.updateChannel(command);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用渠道")
    @PreAuthorize("@ss.hasPermission('ai:channel:update')")
    public CommonResult<Boolean> updateChannelStatus(@Valid @RequestBody ChannelUpdateStatusCommand command) {
        channelService.updateChannelStatus(command);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除渠道")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:channel:delete')")
    public CommonResult<Boolean> deleteChannel(@RequestParam("id") Long id) {
        channelService.deleteChannel(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得渠道分页")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<PageResult<ChannelDTO>> getChannelPage(@Validated ChannelPageQuery query) {
        return success(channelService.getChannelPage(query));
    }

    @GetMapping("/get")
    @Operation(summary = "获得渠道详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<ChannelDTO> getChannel(@RequestParam("id") Long id) {
        return success(channelService.getChannel(id));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用渠道精简列表", description = "供模型管理页签下拉选择")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<List<ChannelDTO>> getEnabledChannelList() {
        return success(channelService.getEnabledChannelList());
    }

    @PostMapping("/test-connectivity")
    @Operation(summary = "渠道连通性测试", description = "用表单当前凭据（不落库）对指定模型标识做一次轻量真实调用，供保存前发现密钥或端点错误")
    @PreAuthorize("@ss.hasPermission('ai:channel:update')")
    public CommonResult<ConnectivityTestDTO> testConnectivity(
            @Valid @RequestBody ChannelConnectivityTestCommand command) {
        return success(channelService.testChannelConnectivity(command));
    }

}
