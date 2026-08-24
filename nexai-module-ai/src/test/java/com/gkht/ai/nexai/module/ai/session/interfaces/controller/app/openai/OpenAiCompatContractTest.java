package com.gkht.ai.nexai.module.ai.session.interfaces.controller.app.openai;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OpenAI 兼容出口契约测试（工单 16）：以 OpenAI 官方 Java SDK 客户端做流式契约验证——
 * 改个 base URL 即接入（SDK → RANDOM_PORT 服务，路径 /app-api/ai/openai/chat/completions），
 * 认证经租户 API Key（过滤器真链路，Key 落 H2）；事件流经桩网关返回固定 chunk 序列
 * （运行时真链路由 AgentscopeRuntimeGatewayTest 覆盖，此处验证出口协议契约）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenAiCompatContractTest.TestApplication.class,
        properties = {"spring.application.name=nexai-openai-contract-test"})
@ActiveProfiles("unit-test")
public class OpenAiCompatContractTest {

    private static final long TEST_TENANT = 1L;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /** 注入以强制初始化过滤器注册 bean（unit-test 懒加载下确保 web server 启动前完成注册） */
    @Resource
    private org.springframework.boot.web.servlet.FilterRegistrationBean<?> apiKeyAuthFilterRegistration;

    /** 注入以强制初始化租户拦截器 bean（向 MybatisPlusInterceptor 注册 inner） */
    @Resource
    private com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantLineInnerInterceptor;

    @MockitoBean
    private com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository agentSpecRepository;

    @MockitoBean
    private com.gkht.ai.nexai.module.ai.session.application.service.AgentRuntimeAssembler runtimeAssembler;

    /** OpenAI 出口 base URL（SDK 拼接 /chat/completions） */
    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    private int serverPort;

    @SpringBootConfiguration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(
            com.gkht.ai.nexai.framework.tenant.config.TenantProperties.class)
    @Import({
            // 最小 web 装配（内嵌 Tomcat + DispatcherServlet + MVC；不经 @EnableAutoConfiguration，
            // 避免拉入芋道各 starter 自动配置的跨模块依赖）
            org.springframework.boot.web.server.autoconfigure.servlet.ServletWebServerConfiguration.class,
            org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class,
            OpenAiCompatController.class,
            com.gkht.ai.nexai.module.ai.session.application.service.OpenAiCompatServiceImpl.class,
            // 生效快照单一入口：出口服务经 agentspec 应用服务解析（repository 已被 mock）
            com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl.class,
            com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl.class,
            com.gkht.ai.nexai.module.ai.framework.config.AiOpenApiConfiguration.class,
            com.gkht.ai.nexai.module.ai.apikey.infrastructure.repository.TenantApiKeyRepositoryImpl.class,
            OpenAiCompatContractTest.H2DbConfiguration.class,
            OpenAiCompatContractTest.FakeOpenAiGatewayConfiguration.class})
    static class TestApplication {
    }

    /** DB 套件（BaseDbUnitTest 同款 + 租户拦截器强制注册） */
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Import({
            com.gkht.ai.nexai.framework.datasource.config.NexaiDataSourceAutoConfiguration.class,
            org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class,
            org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration.class,
            com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure.class,
            com.gkht.ai.nexai.framework.test.config.SqlInitializationTestConfiguration.class,
            com.gkht.ai.nexai.framework.mybatis.config.NexaiMybatisAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            OpenAiCompatContractTest.TenantDbTestConfiguration.class,
    })
    static class H2DbConfiguration {
    }

    /** 手动注册租户拦截器（不引入 NexaiTenantAutoConfiguration 的完整 filter 链，
     *  只需 insert/select 的租户列注入；认证过滤器为平台自注册） */
    @TestConfiguration
    static class TenantDbTestConfiguration {

        @Bean
        public TenantLineInnerInterceptor tenantLineInnerInterceptor(
                com.gkht.ai.nexai.framework.tenant.config.TenantProperties properties,
                com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner = new TenantLineInnerInterceptor(
                    new com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor(properties));
            com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }

        /** URL 前缀机制（对齐 NexaiWebAutoConfiguration：controller.app 包 → /app-api 前缀，
         *  不引入其 filter 链避免最小装配下的额外依赖） */
        @Bean
        public org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations webMvcRegistrations() {
            return new org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations() {
                @Override
                public org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
                getRequestMappingHandlerMapping() {
                    var mapping = new org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping();
                    mapping.setPathPrefixes(java.util.Map.of("/app-api",
                            (java.util.function.Predicate<Class<?>>) clazz ->
                                    clazz.getPackageName().contains(".controller.app.")));
                    return mapping;
                }
            };
        }
    }

    /** 出口网关桩：返回固定 chunk 序列（OpenAI 流式协议形态，SDK 解析契约） */
    @TestConfiguration
    static class FakeOpenAiGatewayConfiguration {

        @Bean
        public AgentRuntimeGateway agentRuntimeGateway() {
            return new AgentRuntimeGateway() {
                @Override
                public Flux<RuntimeEvent> chatOpenAi(AgentRuntimeConfig config,
                                                     List<ChatMessageInput> messages, String requestId) {
                    // 装配指令经 mock assembler（null）——桩不消费 config，model 名固定
                    return Flux.just(
                            chunkEvent(requestId, "contract-agent",
                                    "{\"role\":\"assistant\",\"content\":\"你好\"}", null),
                            chunkEvent(requestId, "contract-agent",
                                    "{\"content\":\"，出口契约正常\"}", null),
                            chunkEvent(requestId, "contract-agent",
                                    "{}", "stop"));
                }

                @Override
                public Flux<RuntimeEvent> chat(AgentRuntimeConfig config, String content) {
                    return Flux.empty();
                }

                @Override
                public Flux<RuntimeEvent> confirmToolCalls(AgentRuntimeConfig config,
                        List<com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision> decisions) {
                    return Flux.empty();
                }

                @Override
                public boolean interrupt(AgentRuntimeConfig config) {
                    return false;
                }

                @Override
                public List<String> listWorkspaceFiles(AgentRuntimeConfig config, String relativePath) {
                    return List.of();
                }

                @Override
                public String readWorkspaceFile(AgentRuntimeConfig config, String relativePath) {
                    return null;
                }

                @Override
                public List<java.util.Map<String, Object>> loadSessionMessages(AgentRuntimeConfig config) {
                    return List.of();
                }

                @Override
                public void closeAll() {
                }
            };
        }

        private static RuntimeEvent chunkEvent(String requestId, String model, String deltaJson,
                                               String finishReason) {
            String choices = "[{\"index\":0,\"delta\":" + deltaJson + ",\"finish_reason\":"
                    + (finishReason == null ? "null" : "\"" + finishReason + "\"") + "}]";
            String payload = "{\"id\":\"" + requestId + "\",\"object\":\"chat.completion.chunk\""
                    + ",\"created\":1700000000,\"model\":\"" + model + "\",\"choices\":" + choices + "}";
            return RuntimeEvent.of(RuntimeEventType.OPENAI_CHUNK, payload);
        }
    }

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TEST_TENANT);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    private String baseUrl() {
        return "http://localhost:" + serverPort + "/app-api/ai/openai";
    }

    /** 落库一把真 Key 并返回明文（认证过滤器真链路消费） */
    private String issueRealKey() {
        String plainKey = "nexai-contract-" + UUID.randomUUID().toString().replace("-", "");
        var key = com.gkht.ai.nexai.module.ai.apikey.domain.model.TenantApiKey.issue(
                "契约测试", plainKey.substring(0, 11),
                com.gkht.ai.nexai.module.ai.shared.util.Hashes.sha256Hex(
                        plainKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
        applicationContext.getBean(
                com.gkht.ai.nexai.module.ai.apikey.domain.repository.TenantApiKeyRepository.class).save(key);
        return plainKey;
    }

    private void stubSpecRouting() {
        var spec = com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec.reconstitute(
                7L, "契约智能体", "contract-agent", null,
                com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel.TENANT, null, null, 1,
                java.time.LocalDateTime.now());
        Mockito.when(agentSpecRepository.findBySpecCode("contract-agent")).thenReturn(spec);
        Mockito.when(agentSpecRepository.listVersions(7L)).thenReturn(List.of(
                com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion.reconstitute(
                        70L, 7L, 1, com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig.of(
                                1L, null, null, null, null, null, null, null, null),
                        null, java.time.LocalDateTime.now())));
    }

    @Test
    @DisplayName("流式契约：OpenAI SDK 改 base URL 接入，chunk 序列与文本聚合符合协议")
    public void streamingContractWithOpenAiSdk() throws Exception {
        stubSpecRouting();
        String plainKey = issueRealKey();
        com.openai.client.OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl())
                .apiKey(plainKey)
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("contract-agent")
                .addUserMessage("你好")
                .build();

        List<ChatCompletionChunk> chunks = new ArrayList<>();
        try (StreamResponse<ChatCompletionChunk> response =
                     client.chat().completions().createStreaming(params)) {
            response.stream().forEach(chunks::add);
        }

        // 契约：chunk 序列非空、id/model 回填、delta 文本聚合完整、末帧 finish_reason=stop
        assertTrue(chunks.size() >= 3, "应收到完整 chunk 序列，实际：" + chunks.size());
        assertEquals("contract-agent", chunks.get(0).model());
        StringBuilder text = new StringBuilder();
        String lastFinish = null;
        for (ChatCompletionChunk chunk : chunks) {
            if (!chunk.choices().isEmpty()) {
                chunk.choices().get(0).delta().content().ifPresent(text::append);
                lastFinish = chunk.choices().get(0).finishReason()
                        .map(Object::toString).orElse(lastFinish);
            }
        }
        assertEquals("你好，出口契约正常", text.toString());
        assertEquals("stop", lastFinish, "末帧 finish_reason 应为 stop");
    }

    @Test
    @DisplayName("认证契约：无效 API Key 被拒（401）")
    public void invalidKeyRejected() {
        stubSpecRouting();
        com.openai.client.OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl())
                .apiKey("nexai-invalid-key-0000000000000000000000")
                .build();
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model("contract-agent")
                .addUserMessage("你好")
                .build();
        assertThrows(Exception.class, () -> {
            try (StreamResponse<ChatCompletionChunk> ignored =
                         client.chat().completions().createStreaming(params)) {
                fail("无效 Key 不应成功建立流");
            }
        });
    }

}
