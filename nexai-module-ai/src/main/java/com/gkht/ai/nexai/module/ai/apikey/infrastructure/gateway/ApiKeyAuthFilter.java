package com.gkht.ai.nexai.module.ai.apikey.infrastructure.gateway;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey;
import com.gkht.ai.nexai.module.ai.apikey.domain.repository.TenantApiKeyRepository;
import com.gkht.ai.nexai.module.ai.shared.util.Hashes;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 租户 API Key 认证过滤器（工单 16）：拦 OpenAI 兼容出口（/app-api/ai/openai/**），
 * {@code Authorization: Bearer <key>}（或 {@code X-API-Key} 头）→ SHA-256 寻址 →
 * 有效即以 Key 的归属租户恢复租户上下文（覆盖请求头 tenant-id——API Key 即租户凭证，
 * 防伪造越权）。失败 401（OpenAI 兼容错误体）。哈希全局唯一，寻址不到 = 无效。
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** OpenAI 兼容出口路径（不含 /app-api 前缀，web starter 包通配符挂载前缀后的路径段） */
    public static final String OPENAI_PATH = "/ai/openai";
    /** 完整出口前缀（含 /app-api）：过滤器拦截判断、URL pattern 与 permitAll 共用此唯一拼写 */
    public static final String OPENAI_FULL_PREFIX = "/app-api" + OPENAI_PATH;
    /** 请求 attribute 名：认证通过的 Key 聚合（出口 controller 校验规格范围用） */
    public static final String ATTRIBUTE_AUTHENTICATED_KEY = "nexaiAuthenticatedApiKey";

    private static final String BEARER_PREFIX = "Bearer ";

    @Resource
    private TenantApiKeyRepository tenantApiKeyRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(OPENAI_FULL_PREFIX + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String plainKey = extractKey(request);
        if (!StringUtils.hasText(plainKey)) {
            reject(response, "缺少 API Key（Authorization: Bearer <key>）");
            return;
        }
        TenantApiKey key = tenantApiKeyRepository.findByKeyHash(
                Hashes.sha256Hex(plainKey.getBytes(StandardCharsets.UTF_8)));
        if (key == null || key.getTenantId() == null || !key.isActive()) {
            reject(response, "API Key 无效或已吊销");
            return;
        }
        TenantContextHolder.setTenantId(key.getTenantId());
        request.setAttribute(ATTRIBUTE_AUTHENTICATED_KEY, key);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** Authorization Bearer 优先（OpenAI 客户端惯例），其次 X-API-Key 头 */
    private static String extractKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String apiKeyHeader = request.getHeader("X-API-Key");
        return apiKeyHeader == null ? null : apiKeyHeader.trim();
    }

    /** 401 拒绝（OpenAI 兼容错误体形态） */
    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":{\"message\":\"" + message
                + "\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}");
    }

}
