package com.gkht.ai.nexai.module.ai.interfaces.controller.admin.platformtool;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.module.ai.shared.tool.PlatformToolDTO;
import com.gkht.ai.nexai.module.ai.shared.tool.PlatformToolEntry;
import com.gkht.ai.nexai.module.ai.shared.tool.PlatformToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 平台工具库条目列表（规格表单挂载区的候选面）。平台工具为代码内注册的共享机制
 * （非租户数据、无编排逻辑），控制器直接读注册表，不经应用服务。
 *
 * <p>包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。</p>
 */
@Tag(name = "管理后台 - 平台工具库")
@RestController
@RequestMapping("/ai/platform-tool")
public class PlatformToolController {

    @Resource
    private PlatformToolRegistry platformToolRegistry;

    @GetMapping("/list")
    @Operation(summary = "获得平台工具库条目列表", description = "挂载编辑面候选列表（source=PLATFORM 的 sourceId 取条目编号）")
    @PreAuthorize("@ss.hasPermission('ai:spec:query')")
    public CommonResult<List<PlatformToolDTO>> getPlatformToolList() {
        return success(platformToolRegistry.listEntries().stream()
                .map(PlatformToolController::toDTO)
                .toList());
    }

    private static PlatformToolDTO toDTO(PlatformToolEntry entry) {
        PlatformToolDTO dto = new PlatformToolDTO();
        dto.setId(entry.getId());
        dto.setCode(entry.getCode());
        dto.setName(entry.getName());
        dto.setDescription(entry.getDescription());
        dto.setToolNames(entry.getToolNames());
        return dto;
    }

}
