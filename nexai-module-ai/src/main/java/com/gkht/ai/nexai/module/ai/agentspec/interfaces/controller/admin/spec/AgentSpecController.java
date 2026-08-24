package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.security.core.util.SecurityFrameworkUtils;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
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
    @Operation(summary = "创建规格", description = "创建智能体规格并携带首个草稿；spec_code 与归属层级创建后不可变")
    @PreAuthorize("@ss.hasPermission('ai:spec:create')")
    public CommonResult<Long> createSpec(@Valid @RequestBody AgentSpecCreateCommand command) {
        return success(agentSpecService.createSpec(command, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得规格分页", description = "业务编码/归属/草稿状态；用户级规格仅归属用户可见")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<PageResult<AgentSpecDTO>> getSpecPage(@Validated AgentSpecPageQuery query) {
        return success(agentSpecService.getSpecPage(query, SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得规格详情", description = "编辑面回填用：主体元数据 + 配置平铺（草稿优先；已发布无草稿时取当前生效快照）")
    @Parameter(name = "id", description = "规格编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<AgentSpecDetailDTO> getSpec(@RequestParam("id") Long id) {
        return success(agentSpecService.getSpec(id));
    }

    @PutMapping("/update")
    @Operation(summary = "更新规格", description = "编辑面：整体替换主体元数据与草稿，只动草稿不触碰已发布快照")
    @PreAuthorize("@ss.hasPermission('ai:spec:update')")
    public CommonResult<Boolean> updateSpec(@Valid @RequestBody AgentSpecUpdateCommand command) {
        agentSpecService.updateSpec(command);
        return success(true);
    }

}
