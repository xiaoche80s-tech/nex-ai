package com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.mybatis.core.type.EncryptTypeHandler;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.model.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ModelPageQuery;
import com.gkht.ai.nexai.module.ai.model.application.service.ChannelServiceImpl;
import com.gkht.ai.nexai.module.ai.model.application.service.ModelServiceImpl;
import com.gkht.ai.nexai.module.ai.model.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ConnectivityResult;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ChannelConverterImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ModelConverterImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ModelMapper;
import com.gkht.ai.nexai.module.ai.model.infrastructure.repository.ChannelRepositoryImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.repository.ModelRepositoryImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.gkht.ai.nexai.framework.common.exception.enums.GlobalErrorCodeConstants.SUCCESS;
import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_PROVIDER_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_DUPLICATE_MODEL_ID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型 S1 接缝测试：直接调用 /admin-api/ai/model/** 对应的控制器方法，
 * 走真实 controller → application → repository → H2 全链路，只断言外部行为。
 *
 * <p>连通性测试经端口打桩（{@link FakeConnectivityGateway}）：不外呼真实网关，
 * 断言链路透传与探测入参（渠道聚合 + 模型标识）正确。</p>
 */
@Import({ModelController.class, ModelServiceImpl.class, ModelRepositoryImpl.class, ModelConverterImpl.class,
        ChannelController.class, ChannelServiceImpl.class, ChannelRepositoryImpl.class, ChannelConverterImpl.class,
        ModelControllerTest.TenantDbTestConfiguration.class,
        ModelControllerTest.FakeGatewayConfiguration.class})
public class ModelControllerTest extends BaseDbAndRedisUnitTest {

    @Resource
    private ModelController modelController;

    @Resource
    private ChannelController channelController;

    @Resource
    private ModelMapper modelMapper;

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private DataSource dataSource;

    /** 注入以强制初始化租户拦截器 bean（向 MybatisPlusInterceptor 注册 inner） */
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;

    /**
     * 连通性网关桩：记录最近一次探测入参，返回脚本化结果（默认成功）
     */
    static class FakeConnectivityGateway implements ModelConnectivityGateway {

        ConnectivityResult nextResult = ConnectivityResult.success(120L, "连通正常");
        Channel probedChannel;
        String probedModelId;

        @Override
        public ConnectivityResult probe(Channel channel, String modelId) {
            this.probedChannel = channel;
            this.probedModelId = modelId;
            return nextResult;
        }

    }

    @TestConfiguration
    static class FakeGatewayConfiguration {

        @Bean
        public FakeConnectivityGateway fakeConnectivityGateway() {
            return new FakeConnectivityGateway();
        }

    }

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

    @Resource
    private FakeConnectivityGateway fakeGateway;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(1L);
        // 注入真实 AES（16 字节密钥），使渠道密钥走真实加密链路（密文落库 + 读取解密）
        AES aes = SecureUtil.aes("0123456789abcdef".getBytes());
        ReflectUtil.setFieldValue(EncryptTypeHandler.class, "aes", aes);
        // Fake gateway 是共享 bean，每个用例前重置脚本，避免用例间状态泄漏
        fakeGateway.nextResult = ConnectivityResult.success(120L, "连通正常");
        fakeGateway.probedChannel = null;
        fakeGateway.probedModelId = null;
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
        // 复位静态 AES，避免测试密钥泄漏到同 JVM 的其他测试类
        ReflectUtil.setFieldValue(EncryptTypeHandler.class, "aes", null);
    }

    /** 建一个 openai 渠道，apiKey 为空（本测试类大多用例无需密钥） */
    private Long createChannel(String name) {
        return createChannel(name, null);
    }

    /** 建一个 openai 渠道，可携带密钥（经 EncryptTypeHandler 真实加密落库） */
    private Long createChannel(String name, String apiKey) {
        ChannelCreateCommand command = new ChannelCreateCommand();
        command.setName(name);
        command.setProvider(ChannelProvider.OPENAI.getCode());
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey(apiKey);
        return channelController.createChannel(command).getData();
    }

    private ModelCreateCommand createCommand(Long channelId) {
        ModelCreateCommand command = new ModelCreateCommand();
        command.setChannelId(channelId);
        command.setModelId("gpt-4o");
        command.setName("GPT-4o 主力");
        command.setContextWindow(128_000);
        command.setInputPrice(new BigDecimal("0.5"));
        command.setOutputPrice(new BigDecimal("2.5"));
        command.setCapabilities(List.of("Chat", "chat", "vision"));
        return command;
    }

    @Test
    @DisplayName("登记模型：能力标签规范化为 JSON 落库，默认启用，租户自动归属")
    public void createModelPersistsNormalizedCapabilities() throws SQLException {
        Long channelId = createChannel("OpenAI 主渠道");

        Long modelId = modelController.createModel(createCommand(channelId)).getData();

        assertNotNull(modelId);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT capabilities, tenant_id FROM ai_model WHERE id = ?")) {
            statement.setLong(1, modelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertEquals("[\"chat\",\"vision\"]", resultSet.getString(1));
                assertEquals(1L, resultSet.getLong(2));
            }
        }
        assertTrue(modelMapper.selectById(modelId).getEnabled());
    }

    @Test
    @DisplayName("登记模型：可选字段（上下文窗口/单价/能力标签）可全部为空")
    public void createModelAllowsEmptyOptionals() {
        Long channelId = createChannel("OpenAI 主渠道");
        ModelCreateCommand command = createCommand(channelId);
        command.setContextWindow(null);
        command.setInputPrice(null);
        command.setOutputPrice(null);
        command.setCapabilities(null);

        Long modelId = modelController.createModel(command).getData();

        assertNull(modelMapper.selectById(modelId).getContextWindow());
        // 能力标签聚合内规范化为空列表，落库即空 JSON 数组
        assertEquals("[]", modelMapper.selectById(modelId).getCapabilities());
    }

    @Test
    @DisplayName("登记模型：渠道不存在时报 CHANNEL_NOT_EXISTS")
    public void createModelChannelNotExists() {
        ModelCreateCommand command = createCommand(999L);

        assertServiceException(() -> modelController.createModel(command), CHANNEL_NOT_EXISTS);
    }

    @Test
    @DisplayName("登记模型：同渠道下模型标识重复报 MODEL_DUPLICATE_MODEL_ID，不同渠道可同标识")
    public void createModelDuplicateModelId() {
        Long channelA = createChannel("渠道 A");
        Long channelB = createChannel("渠道 B");
        modelController.createModel(createCommand(channelA));

        assertServiceException(() -> modelController.createModel(createCommand(channelA)),
                MODEL_DUPLICATE_MODEL_ID);

        // 不同渠道下同标识允许
        assertNotNull(modelController.createModel(createCommand(channelB)).getData());
    }

    @Test
    @DisplayName("更新模型：可改元数据与改挂渠道；改后新渠道下标识唯一性仍受约束")
    public void updateModelMovesChannel() {
        Long channelA = createChannel("渠道 A");
        Long channelB = createChannel("渠道 B");
        Long modelId = modelController.createModel(createCommand(channelA)).getData();

        ModelUpdateCommand command = new ModelUpdateCommand();
        command.setId(modelId);
        command.setChannelId(channelB);
        command.setModelId("gpt-4o-mini");
        command.setName("GPT-4o mini");
        command.setContextWindow(8_000);
        command.setInputPrice(new BigDecimal("0.1"));
        command.setOutputPrice(new BigDecimal("0.4"));
        command.setCapabilities(List.of("tools"));
        modelController.updateModel(command);

        ModelDTO dto = modelController.getModel(modelId).getData();
        assertEquals(channelB, dto.getChannelId());
        assertEquals("gpt-4o-mini", dto.getModelId());
        assertEquals(List.of("tools"), dto.getCapabilities());
    }

    @Test
    @DisplayName("更新模型：把标识改成同渠道下已占用的标识报 MODEL_DUPLICATE_MODEL_ID")
    public void updateModelConflictingModelId() {
        Long channelId = createChannel("渠道 A");
        ModelCreateCommand first = createCommand(channelId);
        first.setModelId("gpt-4o");
        modelController.createModel(first);
        ModelCreateCommand second = createCommand(channelId);
        second.setModelId("gpt-4o-mini");
        Long secondId = modelController.createModel(second).getData();

        ModelUpdateCommand command = new ModelUpdateCommand();
        command.setId(secondId);
        command.setChannelId(channelId);
        command.setModelId("gpt-4o"); // 已被第一条占用
        command.setName("改名");

        assertServiceException(() -> modelController.updateModel(command), MODEL_DUPLICATE_MODEL_ID);
    }

    @Test
    @DisplayName("更新模型：不存在时报 MODEL_NOT_EXISTS")
    public void updateModelNotExists() {
        Long channelId = createChannel("渠道 A");
        ModelUpdateCommand command = new ModelUpdateCommand();
        command.setId(999L);
        command.setChannelId(channelId);
        command.setModelId("gpt-4o");
        command.setName("名");

        assertServiceException(() -> modelController.updateModel(command), MODEL_NOT_EXISTS);
    }

    @Test
    @DisplayName("启停模型：停用后按启用过滤不可见，可再启用")
    public void updateModelStatus() {
        Long channelId = createChannel("渠道 A");
        Long modelId = modelController.createModel(createCommand(channelId)).getData();

        ModelUpdateStatusCommand disable = new ModelUpdateStatusCommand();
        disable.setId(modelId);
        disable.setEnabled(false);
        assertEquals(SUCCESS.getCode(), modelController.updateModelStatus(disable).getCode());
        assertFalse(modelMapper.selectById(modelId).getEnabled());

        ModelPageQuery enabledOnly = new ModelPageQuery();
        enabledOnly.setEnabled(true);
        assertEquals(0, modelController.getModelPage(enabledOnly).getData().getTotal());

        ModelUpdateStatusCommand enable = new ModelUpdateStatusCommand();
        enable.setId(modelId);
        enable.setEnabled(true);
        modelController.updateModelStatus(enable);
        assertTrue(modelMapper.selectById(modelId).getEnabled());
    }

    @Test
    @DisplayName("删除模型：逻辑删除后详情与分页均不可见")
    public void deleteModel() {
        Long channelId = createChannel("渠道 A");
        Long modelId = modelController.createModel(createCommand(channelId)).getData();

        assertEquals(SUCCESS.getCode(), modelController.deleteModel(modelId).getCode());

        assertNull(modelMapper.selectById(modelId));
        assertServiceException(() -> modelController.getModel(modelId), MODEL_NOT_EXISTS);
        assertEquals(0, modelController.getModelPage(new ModelPageQuery()).getData().getTotal());
    }

    @Test
    @DisplayName("分页查询：按渠道/标识/名称/能力标签过滤，DTO 补充渠道名称与提供商")
    public void getModelPageWithConditions() {
        Long channelA = createChannel("OpenAI 主渠道");
        Long channelB = createChannel("中转渠道");
        ModelCreateCommand gpt = createCommand(channelA);
        gpt.setCapabilities(List.of("chat"));
        modelController.createModel(gpt);
        ModelCreateCommand relay = createCommand(channelB);
        relay.setModelId("qwen-plus");
        relay.setName("通义千问");
        relay.setCapabilities(List.of("chat", "tools"));
        modelController.createModel(relay);

        ModelPageQuery byChannel = new ModelPageQuery();
        byChannel.setChannelId(channelA);
        PageResult<ModelDTO> page = modelController.getModelPage(byChannel).getData();
        assertEquals(1, page.getTotal());
        assertEquals("OpenAI 主渠道", page.getList().get(0).getChannelName());
        assertEquals("openai", page.getList().get(0).getChannelProvider());

        ModelPageQuery byModelId = new ModelPageQuery();
        byModelId.setModelId("qwen");
        assertEquals(1, modelController.getModelPage(byModelId).getData().getTotal());

        ModelPageQuery byName = new ModelPageQuery();
        byName.setName("通义");
        assertEquals(1, modelController.getModelPage(byName).getData().getTotal());

        ModelPageQuery byCapability = new ModelPageQuery();
        byCapability.setCapability("tools");
        assertEquals(1, modelController.getModelPage(byCapability).getData().getTotal());
    }

    @Test
    @DisplayName("连通性测试：成功结果透传，探测入参为所属渠道聚合与模型标识")
    public void testConnectivitySuccess() {
        Long channelId = createChannel("OpenAI 主渠道");
        Long modelId = modelController.createModel(createCommand(channelId)).getData();

        ConnectivityTestDTO result = modelController.testConnectivity(modelId).getData();

        assertTrue(result.getSuccess());
        assertEquals(120L, result.getDurationMs());
        assertEquals("连通正常", result.getMessage());
        // 探测拿到的是模型所属渠道与模型标识
        assertEquals(channelId, fakeGateway.probedChannel.getId());
        assertEquals("gpt-4o", fakeGateway.probedModelId);
    }

    @Test
    @DisplayName("连通性测试：失败（如凭据错误）同样以结果返回而非异常，携带耗时与原因")
    public void testConnectivityFailureReturnsResult() {
        Long channelId = createChannel("OpenAI 主渠道");
        Long modelId = modelController.createModel(createCommand(channelId)).getData();
        fakeGateway.nextResult = ConnectivityResult.failure(3_000L, "HttpException: 401 Unauthorized");

        ConnectivityTestDTO result = modelController.testConnectivity(modelId).getData();

        assertFalse(result.getSuccess());
        assertEquals(3_000L, result.getDurationMs());
        assertEquals("HttpException: 401 Unauthorized", result.getMessage());
    }

    @Test
    @DisplayName("连通性测试：模型不存在或所属渠道已被删除均报错而非外呼")
    public void testConnectivityPreconditions() {
        assertServiceException(() -> modelController.testConnectivity(999L), MODEL_NOT_EXISTS);

        Long channelId = createChannel("将删除的渠道");
        Long modelId = modelController.createModel(createCommand(channelId)).getData();
        channelController.deleteChannel(channelId);

        assertServiceException(() -> modelController.testConnectivity(modelId), CHANNEL_NOT_EXISTS);
        assertNull(fakeGateway.probedChannel);
    }

    @Test
    @DisplayName("租户隔离：租户 1 的模型对租户 2 不可见")
    public void tenantIsolation() {
        Long channelId = createChannel("OpenAI 主渠道");
        modelController.createModel(createCommand(channelId));

        TenantContextHolder.setTenantId(2L);
        assertEquals(0, modelController.getModelPage(new ModelPageQuery()).getData().getTotal());

        TenantContextHolder.setTenantId(1L);
        assertEquals(1, modelController.getModelPage(new ModelPageQuery()).getData().getTotal());
    }

    @Test
    @DisplayName("渠道连通性测试（保存前）：表单凭据不落库直接探测，临时渠道无编号")
    public void testChannelConnectivityWithFormCredentials() {
        ChannelConnectivityTestCommand command = new ChannelConnectivityTestCommand();
        command.setProvider(ChannelProvider.OPENAI.getCode());
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey("form-key-1234567890");
        command.setModelId("gpt-4o");

        ConnectivityTestDTO result = channelController.testConnectivity(command).getData();

        assertTrue(result.getSuccess());
        assertEquals("gpt-4o", fakeGateway.probedModelId);
        // 探测目标为临时渠道：凭据取自表单、未落库无编号
        assertNull(fakeGateway.probedChannel.getId());
        assertEquals("https://api.openai.com/v1", fakeGateway.probedChannel.getBaseUrl());
        assertEquals("form-key-1234567890", fakeGateway.probedChannel.getApiKey());
        assertEquals(0, channelMapper.selectCount());
    }

    @Test
    @DisplayName("渠道连通性测试（编辑态）：密钥留空且传渠道编号时回退已存密钥")
    public void testChannelConnectivityFallsBackToStoredApiKey() {
        Long storedChannelId = createChannel("已存渠道", "stored-key-0987654321");

        ChannelConnectivityTestCommand command = new ChannelConnectivityTestCommand();
        command.setProvider(ChannelProvider.OPENAI.getCode());
        command.setBaseUrl("https://api.openai.com/v1");
        command.setChannelId(storedChannelId);
        command.setModelId("gpt-4o");

        channelController.testConnectivity(command).getData();

        // 已存密钥经 EncryptTypeHandler 密文落库、读取时解密回明文供探测
        assertEquals("stored-key-0987654321", fakeGateway.probedChannel.getApiKey());
    }

    @Test
    @DisplayName("渠道连通性测试：提供商类型非法报 CHANNEL_PROVIDER_INVALID")
    public void testChannelConnectivityProviderInvalid() {
        ChannelConnectivityTestCommand command = new ChannelConnectivityTestCommand();
        command.setProvider("unknown-provider");
        command.setBaseUrl("https://api.openai.com/v1");
        command.setModelId("gpt-4o");

        assertServiceException(() -> channelController.testConnectivity(command), CHANNEL_PROVIDER_INVALID);
    }

}
