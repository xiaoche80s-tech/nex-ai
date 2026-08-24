package com.gkht.ai.nexai.module.ai.apikey.interfaces.controller.admin.apikey;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.apikey.application.command.ApiKeyCreateCommand;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.ApiKeyCreatedDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.dto.TenantApiKeyDTO;
import com.gkht.ai.nexai.module.ai.apikey.application.query.ApiKeyPageQuery;
import com.gkht.ai.nexai.module.ai.apikey.application.service.ApiKeyServiceImpl;
import com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey;
import com.gkht.ai.nexai.module.ai.apikey.domain.repository.TenantApiKeyRepository;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.repository.TenantApiKeyRepositoryImpl;
import com.gkht.ai.nexai.module.ai.shared.util.Hashes;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.API_KEY_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户 API Key HTTP 契约测试（工单 16，H2 全链）：生成（明文一次性返回 + 密文落库）、
 * 分页（不含明文/哈希）、吊销（幂等状态迁移）、按哈希寻址（认证路径，忽略租户）。
 */
@Import({ApiKeyController.class, ApiKeyServiceImpl.class, TenantApiKeyRepositoryImpl.class,
        com.gkht.ai.nexai.module.ai.apikey.infrastructure.converter.ApiKeyConverterImpl.class,
        ApiKeyControllerTest.TenantDbTestConfiguration.class})
public class ApiKeyControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;
    private static final long TENANT_TWO = 2L;

    @Resource
    private ApiKeyController apiKeyController;

    @Resource
    private TenantApiKeyRepository tenantApiKeyRepository;

    /** 注入以强制初始化租户拦截器 bean（向 MybatisPlusInterceptor 注册 inner） */
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;

    @TestConfiguration
    @EnableConfigurationProperties(TenantProperties.class)
    static class TenantDbTestConfiguration {

        @Bean
        public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                                     MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner =
                    new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }

    }

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    private ApiKeyCreateCommand createCommand(String name, List<String> specCodes) {
        ApiKeyCreateCommand command = new ApiKeyCreateCommand();
        command.setName(name);
        command.setSpecCodes(specCodes);
        return command;
    }

    @Test
    @DisplayName("生成：明文仅返回一次，落库为哈希与识别前缀，前缀为明文截取")
    public void createKeyReturnsPlaintextOnceAndStoresHash() {
        ApiKeyCreatedDTO created = apiKeyController.createKey(createCommand("集成专用", null)).getData();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertTrue(created.getApiKey().startsWith("nexai-"), "明文 Key 应带 nexai- 前缀");
        assertTrue(created.getApiKey().length() > 40, "明文 Key 应含足够熵的随机段");
        assertEquals(created.getApiKey().substring(0, 11), created.getKeyPrefix());

        // 密文口径：落库记录哈希 = 明文 SHA-256；明文不再可取
        TenantApiKey stored = tenantApiKeyRepository.findByKeyHash(Hashes.sha256Hex(
                created.getApiKey().getBytes(StandardCharsets.UTF_8)));
        assertNotNull(stored, "按明文哈希应寻址到落库记录（认证路径）");
        assertEquals("集成专用", stored.getName());
        assertTrue(stored.isActive());
        assertTrue(stored.getSpecCodes().isEmpty(), "空规格范围 = 本租户全部规格");
    }

    @Test
    @DisplayName("分页：含识别前缀/状态/规格范围，不含明文与哈希")
    public void pageQueryExcludesSecrets() {
        apiKeyController.createKey(createCommand("key-a", List.of("customer-service"))).getData();
        apiKeyController.createKey(createCommand("key-b", null)).getData();

        PageResult<TenantApiKeyDTO> page = apiKeyController.getKeyPage(new ApiKeyPageQuery()).getData();
        assertEquals(2, page.getTotal());
        TenantApiKeyDTO dto = page.getList().get(0);
        assertTrue(dto.getKeyPrefix().startsWith("nexai-"));
        assertEquals("ENABLED", dto.getStatus());
    }

    @Test
    @DisplayName("吊销：状态置 REVOKED（幂等），认证路径随即失效")
    public void revokeDisablesAuthentication() {
        ApiKeyCreatedDTO created = apiKeyController.createKey(createCommand("待吊销", null)).getData();
        String keyHash = Hashes.sha256Hex(created.getApiKey().getBytes(StandardCharsets.UTF_8));

        apiKeyController.revokeKey(created.getId());
        // 幂等
        apiKeyController.revokeKey(created.getId());

        TenantApiKey stored = tenantApiKeyRepository.findByKeyHash(keyHash);
        assertNotNull(stored);
        assertEquals("REVOKED", stored.getStatus().name());
        assertTrue(!stored.isActive(), "吊销后认证校验应拒绝");
    }

    @Test
    @DisplayName("吊销不存在的 Key 被拒（业务错误码）")
    public void revokeMissingKeyRejected() {
        assertServiceException(() -> apiKeyController.revokeKey(9999L), API_KEY_NOT_EXISTS);
    }

    @Test
    @DisplayName("规格范围：白名单限定的 Key 只放行名单内规格")
    public void specScopeNarrowsAccess() {
        ApiKeyCreatedDTO created = apiKeyController.createKey(
                createCommand("受限", List.of("customer-service"))).getData();
        TenantApiKey stored = tenantApiKeyRepository.findByKeyHash(Hashes.sha256Hex(
                created.getApiKey().getBytes(StandardCharsets.UTF_8)));
        assertTrue(stored.allowsSpec("customer-service"));
        assertTrue(!stored.allowsSpec("other-agent"), "白名单外规格应拒绝");
    }

    @Test
    @DisplayName("租户隔离：租户二查不到租户一的 Key；按哈希寻址跨租户可命中（全局唯一认证路径）")
    public void tenantIsolationOnPageAndGlobalLookupByHash() {
        ApiKeyCreatedDTO created = apiKeyController.createKey(createCommand("租户一", null)).getData();

        TenantContextHolder.setTenantId(TENANT_TWO);
        assertEquals(0, apiKeyController.getKeyPage(new ApiKeyPageQuery()).getData().getTotal(),
                "租户二分页不应见到租户一的 Key");
        // 认证路径忽略租户（寻址即鉴权）
        TenantApiKey byHash = tenantApiKeyRepository.findByKeyHash(Hashes.sha256Hex(
                created.getApiKey().getBytes(StandardCharsets.UTF_8)));
        assertNotNull(byHash);
        assertNotEquals(TENANT_TWO, byHash.getTenantId());
        assertEquals(TENANT_ONE, byHash.getTenantId());
    }

}
