package com.gkht.ai.nexai.module.ai.apikey.interfaces.controller.admin.apikey;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.apikey.application.command.ApiKeyCreateCommand;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.ApiKeyCreatedDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.TenantApiKeyDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.query.ApiKeyPageQuery;
import com.gkht.ai.nexai.module.ai.apikey.application.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.gkht.ai.nexai.framework.common.pojo.CommonResult.success;

/**
 * 包名含 controller.admin 段，由 web starter 包通配符规则挂载 /admin-api 前缀。
 */
@Tag(name = "管理后台 - 租户 API Key")
@RestController
@RequestMapping("/ai/api-key")
@Validated
public class ApiKeyController {

    @Resource
    private ApiKeyService apiKeyService;

    @PostMapping("/create")
    @Operation(summary = "生成 API Key",
            description = "服务端生成明文 Key 并密文落库（SHA-256）；明文仅本次响应返回一次，请妥善保存")
    @PreAuthorize("@ss.hasPermission('ai:api-key:create')")
    public CommonResult<ApiKeyCreatedDTO> createKey(@Valid @RequestBody ApiKeyCreateCommand command) {
        return success(apiKeyService.createKey(command));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 API Key 分页", description = "不含明文与哈希（密文不回传）")
    @PreAuthorize("@ss.hasPermission('ai:api-key:query')")
    public CommonResult<PageResult<TenantApiKeyDTO>> getKeyPage(@Validated ApiKeyPageQuery query) {
        return success(apiKeyService.getKeyPage(query));
    }

    @DeleteMapping("/revoke")
    @Operation(summary = "吊销 API Key", description = "状态单向置 REVOKED（泄漏止损），幂等")
    @PreAuthorize("@ss.hasPermission('ai:api-key:revoke')")
    public CommonResult<Boolean> revokeKey(@RequestParam("id") Long id) {
        apiKeyService.revokeKey(id);
        return success(true);
    }

}
