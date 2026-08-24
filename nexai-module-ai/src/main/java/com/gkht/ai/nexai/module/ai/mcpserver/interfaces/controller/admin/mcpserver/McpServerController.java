package com.gkht.ai.nexai.module.ai.mcpserver.interfaces.controller.admin.mcpserver;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerCreateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.command.McpServerUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpProbeResultDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.dto.McpServerDTO;
import com.gkht.ai.nexai.module.ai.mcpserver.application.query.McpServerPageQuery;
import com.gkht.ai.nexai.module.ai.mcpserver.application.service.McpServerService;
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
@Tag(name = "管理后台 - MCP Server")
@RestController
@RequestMapping("/ai/mcp-server")
@Validated
public class McpServerController {

    @Resource
    private McpServerService mcpServerService;

    @PostMapping("/create")
    @Operation(summary = "注册 MCP Server", description = "三传输（stdio/SSE/StreamableHTTP）+ 认证；MVP 租户级（BYO-MCP）")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:create')")
    public CommonResult<Long> createMcpServer(@Valid @RequestBody McpServerCreateCommand command) {
        return success(mcpServerService.createMcpServer(command));
    }

    @PutMapping("/update")
    @Operation(summary = "更新 MCP Server", description = "接入配置整体替换；认证头 null = 保留原值")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:update')")
    public CommonResult<Boolean> updateMcpServer(@Valid @RequestBody McpServerUpdateCommand command) {
        mcpServerService.updateMcpServer(command);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "启用/停用 MCP Server")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:update')")
    public CommonResult<Boolean> updateMcpServerStatus(@Valid @RequestBody McpServerUpdateStatusCommand command) {
        mcpServerService.updateMcpServerStatus(command);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 MCP Server")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:delete')")
    public CommonResult<Boolean> deleteMcpServer(@RequestParam("id") Long id) {
        mcpServerService.deleteMcpServer(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得 MCP Server 详情", description = "不含认证头与环境变量（凭证面）")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:query')")
    public CommonResult<McpServerDTO> getMcpServer(@RequestParam("id") Long id) {
        return success(mcpServerService.getMcpServer(id));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 MCP Server 分页")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:query')")
    public CommonResult<PageResult<McpServerDTO>> getMcpServerPage(@Validated McpServerPageQuery query) {
        return success(mcpServerService.getMcpServerPage(query));
    }

    @PostMapping("/probe")
    @Operation(summary = "连通探测并拉取工具清单", description = "initialize + listTools；成功时工具名清单回写缓存（挂载编辑面候选项）")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('ai:mcp-server:probe')")
    public CommonResult<McpProbeResultDTO> probeMcpServer(@RequestParam("id") Long id) {
        return success(mcpServerService.probeMcpServer(id));
    }

}
