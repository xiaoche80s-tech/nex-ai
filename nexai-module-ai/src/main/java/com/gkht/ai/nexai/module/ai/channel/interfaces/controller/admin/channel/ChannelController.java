package com.gkht.ai.nexai.module.ai.channel.interfaces.controller.admin.channel;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ChannelPageQuery;
import com.gkht.ai.nexai.module.ai.channel.application.service.ChannelService;
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
@Tag(name = "管理后台 - 模型渠道（BYOK）")
@RestController
@RequestMapping("/ai/channel")
@Validated
public class ChannelController {

    @Resource
    private ChannelService channelService;

    @PostMapping("/create")
    @Operation(summary = "创建渠道", description = "租户自带密钥（BYOK）；归属固定租户侧")
    @PreAuthorize("@ss.hasPermission('ai:channel:create')")
    public CommonResult<Long> createChannel(@Valid @RequestBody ChannelCreateCommand command) {
        return success(channelService.createChannel(command));
    }

    @PutMapping("/update")
    @Operation(summary = "更新渠道", description = "apiKey 留空表示保留原密钥")
    @PreAuthorize("@ss.hasPermission('ai:channel:update')")
    public CommonResult<Boolean> updateChannel(@Valid @RequestBody ChannelUpdateCommand command) {
        channelService.updateChannel(command);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启停渠道")
    @PreAuthorize("@ss.hasPermission('ai:channel:update')")
    public CommonResult<Boolean> updateChannelStatus(@Valid @RequestBody ChannelUpdateStatusCommand command) {
        channelService.updateChannelStatus(command);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除渠道", description = "级联删除其下全部模型")
    @Parameter(name = "id", description = "渠道编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:channel:delete')")
    public CommonResult<Boolean> deleteChannel(@RequestParam("id") Long id) {
        channelService.deleteChannel(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得渠道分页", description = "密钥脱敏出参")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<PageResult<ChannelDTO>> getChannelPage(@Validated ChannelPageQuery query) {
        return success(channelService.getChannelPage(query));
    }

    @GetMapping("/get")
    @Operation(summary = "获得渠道详情", description = "编辑回显；密钥脱敏，仅返回是否已配置")
    @Parameter(name = "id", description = "渠道编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<ChannelDTO> getChannel(@RequestParam("id") Long id) {
        return success(channelService.getChannel(id));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用渠道简要列表", description = "模型表单的渠道下拉")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<List<ChannelDTO>> getEnabledChannelList() {
        return success(channelService.getEnabledChannelList());
    }

    @PostMapping("/connectivity-test")
    @Operation(summary = "连通性探测", description = "表单凭据即测不落库；携带 channelId 且密钥留空时回退已存密钥")
    @PreAuthorize("@ss.hasPermission('ai:channel:query')")
    public CommonResult<ConnectivityTestDTO> testChannelConnectivity(
            @Valid @RequestBody ChannelConnectivityTestCommand command) {
        return success(channelService.testChannelConnectivity(command));
    }

}
