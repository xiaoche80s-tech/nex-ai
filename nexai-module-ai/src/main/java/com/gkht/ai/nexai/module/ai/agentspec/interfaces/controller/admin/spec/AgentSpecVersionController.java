package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
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

import java.util.List;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 * 版本快照的发布/列表/切换入口（与规格主体同聚合，挂在规格资源路径下）。
 */
@Tag(name = "管理后台 - 智能体规格版本")
@RestController
@RequestMapping("/ai/spec")
@Validated
public class AgentSpecVersionController {

    @Resource
    private AgentSpecService agentSpecService;

    @PostMapping("/publish")
    @Operation(summary = "发布规格版本", description = "把当前草稿固化为不可变版本快照（全量四层配置）并推进当前版本；版本号 = 现有最大 + 1")
    @PreAuthorize("@ss.hasPermission('ai:spec:publish')")
    public CommonResult<Integer> publishSpec(@Valid @RequestBody AgentSpecPublishCommand command) {
        return success(agentSpecService.publishSpec(command));
    }

    @GetMapping("/version-page")
    @Operation(summary = "获得规格版本列表", description = "按版本号升序；当前生效版本带标记（不可变快照的元信息，不含全量配置）")
    @Parameter(name = "specId", description = "规格编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<List<AgentSpecVersionDTO>> listSpecVersions(@RequestParam("specId") Long specId) {
        return success(agentSpecService.listSpecVersions(specId));
    }

    @PutMapping("/switch-version")
    @Operation(summary = "切换当前版本", description = "仅回退当前版本指针（运行寻址），快照本身不可变")
    @PreAuthorize("@ss.hasPermission('ai:spec:update')")
    public CommonResult<Boolean> switchSpecVersion(@Valid @RequestBody AgentSpecSwitchVersionCommand command) {
        agentSpecService.switchSpecVersion(command);
        return success(true);
    }

}
