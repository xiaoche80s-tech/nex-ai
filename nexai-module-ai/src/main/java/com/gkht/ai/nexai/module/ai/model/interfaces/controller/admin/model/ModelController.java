package com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.model.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ModelPageQuery;
import com.gkht.ai.nexai.module.ai.model.application.service.ModelService;
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

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 模型元数据")
@RestController
@RequestMapping("/ai/model")
@Validated
public class ModelController {

    @Resource
    private ModelService modelService;

    @PostMapping("/create")
    @Operation(summary = "登记模型")
    @PreAuthorize("@ss.hasPermission('ai:model:create')")
    public CommonResult<Long> createModel(@Valid @RequestBody ModelCreateCommand command) {
        return success(modelService.createModel(command));
    }

    @PutMapping("/update")
    @Operation(summary = "更新模型")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<Boolean> updateModel(@Valid @RequestBody ModelUpdateCommand command) {
        modelService.updateModel(command);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用模型")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<Boolean> updateModelStatus(@Valid @RequestBody ModelUpdateStatusCommand command) {
        modelService.updateModelStatus(command);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除模型")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:model:delete')")
    public CommonResult<Boolean> deleteModel(@RequestParam("id") Long id) {
        modelService.deleteModel(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得模型分页")
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<PageResult<ModelDTO>> getModelPage(@Validated ModelPageQuery query) {
        return success(modelService.getModelPage(query));
    }

    @GetMapping("/get")
    @Operation(summary = "获得模型详情")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:model:query')")
    public CommonResult<ModelDTO> getModel(@RequestParam("id") Long id) {
        return success(modelService.getModel(id));
    }

    @PostMapping("/test-connectivity")
    @Operation(summary = "连通性测试", description = "用模型所属渠道的凭据做一次轻量真实调用（maxTokens=1），返回成败/耗时/错误信息")
    @Parameter(name = "id", description = "模型编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:model:update')")
    public CommonResult<ConnectivityTestDTO> testConnectivity(@RequestParam("id") Long id) {
        return success(modelService.testConnectivity(id));
    }

}
