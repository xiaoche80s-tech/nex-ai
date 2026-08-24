package com.gkht.ai.nexai.module.ai.apikey.application.service;

import com.gkht.ai.nexai.module.ai.apikey.application.command.ApiKeyCreateCommand;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.ApiKeyCreatedDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.TenantApiKeyDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.query.ApiKeyPageQuery;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;

/**
 * 租户 API Key 应用服务端口（工单 16）：生成 / 分页 / 吊销 / 校验。
 */
public interface ApiKeyService {

    /** 生成 Key（明文仅返回一次；密文存储） */
    ApiKeyCreatedDTO createKey(ApiKeyCreateCommand command);

    /** 分页（不含明文/哈希） */
    PageResult<TenantApiKeyDTO> getKeyPage(ApiKeyPageQuery query);

    /** 吊销（泄漏止损，幂等） */
    void revokeKey(Long id);

}
