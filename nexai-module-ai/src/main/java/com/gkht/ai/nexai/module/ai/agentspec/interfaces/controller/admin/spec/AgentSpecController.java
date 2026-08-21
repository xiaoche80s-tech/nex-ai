package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
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
@Tag(name = "管理后台 - 智能体规格")
@RestController
@RequestMapping("/ai/spec")
@Validated
public class AgentSpecController {

    @Resource
    private AgentSpecService agentSpecService;

    @PostMapping("/create")
    @Operation(summary = "创建规格", description = "创建智能体规格并携带首个草稿")
    @PreAuthorize("@ss.hasPermission('ai:spec:create')")
    public CommonResult<Long> createSpec(@Valid @RequestBody AgentSpecCreateCommand command) {
        return success(agentSpecService.createSpec(command));
    }

    @PutMapping("/update")
    @Operation(summary = "编辑规格", description = "更新主体信息并覆盖草稿；发布后再编辑即生成新草稿")
    @PreAuthorize("@ss.hasPermission('ai:spec:update')")
    public CommonResult<Boolean> updateSpec(@Valid @RequestBody AgentSpecUpdateCommand command) {
        agentSpecService.updateSpec(command);
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布规格", description = "当前草稿固化为不可变新版本，默认版本指针前移；无草稿时报错")
    @PreAuthorize("@ss.hasPermission('ai:spec:publish')")
    public CommonResult<Integer> publishSpec(@Valid @RequestBody AgentSpecPublishCommand command) {
        return success(agentSpecService.publishSpec(command));
    }

    @PutMapping("/switch-default-version")
    @Operation(summary = "切换默认版本", description = "把当前默认版本切到指定已发布版本（回滚/迭代入口）")
    @PreAuthorize("@ss.hasPermission('ai:spec:update')")
    public CommonResult<Boolean> switchDefaultVersion(@Valid @RequestBody AgentSpecSwitchVersionCommand command) {
        agentSpecService.switchDefaultVersion(command);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除规格", description = "删除规格及其全部版本")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:spec:delete')")
    public CommonResult<Boolean> deleteSpec(@RequestParam("id") Long id) {
        agentSpecService.deleteSpec(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得规格分页")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<PageResult<AgentSpecDTO>> getSpecPage(@Validated AgentSpecPageQuery query) {
        return success(agentSpecService.getSpecPage(query));
    }

    @GetMapping("/get")
    @Operation(summary = "获得规格详情", description = "含草稿与当前默认版本快照，编辑表单按此预填")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<AgentSpecDetailDTO> getSpec(@RequestParam("id") Long id) {
        return success(agentSpecService.getSpec(id));
    }

    @GetMapping("/version/list")
    @Operation(summary = "获得版本历史", description = "按版本号倒序，含各版本全量快照")
    @Parameter(name = "specId", description = "规格编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<List<AgentSpecVersionDTO>> getVersionList(@RequestParam("specId") Long specId) {
        return success(agentSpecService.getVersionList(specId));
    }

}
