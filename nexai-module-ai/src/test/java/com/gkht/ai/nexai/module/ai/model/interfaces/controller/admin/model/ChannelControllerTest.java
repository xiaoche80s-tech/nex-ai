package com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.mybatis.core.type.EncryptTypeHandler;
import com.gkht.ai.nexai.framework.security.core.LoginUser;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ChannelPageQuery;
import com.gkht.ai.nexai.module.ai.model.application.service.ChannelServiceImpl;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ChannelConverterImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.model.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.model.infrastructure.repository.ChannelRepositoryImpl;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.gkht.ai.nexai.framework.common.exception.enums.GlobalErrorCodeConstants.SUCCESS;
import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_PROVIDER_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道 S1 接缝测试：直接调用 /admin-api/ai/channel/** 对应的控制器方法，
 * 走真实 controller → application → repository → H2 全链路，只断言外部行为。
 *
 * <p>租户隔离断言依赖 TenantLineInnerInterceptor（单测基类默认不装配，此处单独引入）；
 * 密文落库断言经原生 JDBC 绕过 TypeHandler 读取 api_key 原始列值。
 * 密钥值为虚构测试值，仅用于验证 AES 加解密链路，并非真实凭据。</p>
 */
@Import({ChannelController.class, ChannelServiceImpl.class,
        ChannelRepositoryImpl.class, ChannelConverterImpl.class,
        ChannelControllerTest.TenantDbTestConfiguration.class})
public class ChannelControllerTest extends BaseDbAndRedisUnitTest {

    /** 虚构测试密钥（> 8 位以覆盖脱敏分支），非真实凭据 */
    private static final String PLAIN_TEST_KEY = "test-key-1234567890";
    private static final Long LOGIN_USER_ID = 1L;

    @Resource
    private ChannelController channelController;

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private DataSource dataSource;

    /** 测试上下文开启懒加载，注入以强制初始化租户拦截器 bean（向 MybatisPlusInterceptor 注册 inner） */
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
        TenantContextHolder.setTenantId(1L);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(LOGIN_USER_ID);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(loginUser, null, "mock"));
        // 注入真实 AES（16 字节密钥），使 EncryptTypeHandler 真实加解密而非 mock 直通
        AES aes = SecureUtil.aes("0123456789abcdef".getBytes());
        ReflectUtil.setFieldValue(EncryptTypeHandler.class, "aes", aes);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private ChannelCreateCommand createCommand() {
        ChannelCreateCommand command = new ChannelCreateCommand();
        command.setName("OpenAI 主渠道");
        command.setProvider(ChannelProvider.OPENAI.getCode());
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey(PLAIN_TEST_KEY);
        return command;
    }

    /** 经原生 JDBC 读取 api_key 原始列值，绕过 TypeHandler 的自动解密 */
    private String storedApiKey(Long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT api_key FROM ai_channel WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    /** 经原生 JDBC 读取 tenant_id 原始列值，验证租户拦截器的自动填充 */
    private Long storedTenantId(Long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT tenant_id FROM ai_channel WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("创建渠道：密钥密文落库（库中非明文），读回经 TypeHandler 解密还原，租户自动归属当前租户")
    public void createChannelEncryptedAtRest() throws SQLException {
        Long channelId = channelController.createChannel(createCommand()).getData();

        assertNotNull(channelId);
        // 库中为密文：既不等于明文，也确实非空
        String cipherText = storedApiKey(channelId);
        assertNotNull(cipherText);
        assertNotEquals(PLAIN_TEST_KEY, cipherText);
        // 经 Mapper（TypeHandler 解密）读回为明文
        ChannelDO inserted = channelMapper.selectById(channelId);
        assertEquals(PLAIN_TEST_KEY, inserted.getApiKey());
        assertEquals("OpenAI 主渠道", inserted.getName());
        assertEquals(ChannelProvider.OPENAI.getCode(), inserted.getProvider());
        assertEquals("https://api.openai.com/v1", inserted.getBaseUrl());
        assertTrue(inserted.getEnabled());
        assertEquals("tenant", inserted.getOwnerType());
        // 租户上下文经 TenantLineInnerInterceptor 自动填充
        assertEquals(1L, storedTenantId(channelId));
    }

    @Test
    @DisplayName("创建渠道：ollama 等本地服务可无密钥")
    public void createChannelWithoutApiKey() throws SQLException {
        ChannelCreateCommand command = new ChannelCreateCommand();
        command.setName("本地 Ollama");
        command.setProvider(ChannelProvider.OLLAMA.getCode());
        command.setBaseUrl("http://127.0.0.1:11434");

        Long channelId = channelController.createChannel(command).getData();

        assertNull(storedApiKey(channelId));
        assertNull(channelMapper.selectById(channelId).getApiKey());
    }

    @Test
    @DisplayName("创建渠道：未知提供商类型报 CHANNEL_PROVIDER_INVALID")
    public void createChannelProviderInvalid() {
        ChannelCreateCommand command = createCommand();
        command.setProvider("unknown-provider");

        assertServiceException(() -> channelController.createChannel(command), CHANNEL_PROVIDER_INVALID);
    }

    @Test
    @DisplayName("更新渠道：不传密钥保留原密钥，传新密钥覆盖且仍密文落库")
    public void updateChannelApiKeySemantics() throws SQLException {
        Long channelId = channelController.createChannel(createCommand()).getData();

        ChannelUpdateCommand command = new ChannelUpdateCommand();
        command.setId(channelId);
        command.setName("改名渠道");
        command.setProvider(ChannelProvider.DASHSCOPE.getCode());
        command.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        channelController.updateChannel(command);

        assertEquals(PLAIN_TEST_KEY, channelMapper.selectById(channelId).getApiKey());

        command.setApiKey("updated-key-0987654321");
        channelController.updateChannel(command);

        assertEquals("updated-key-0987654321", channelMapper.selectById(channelId).getApiKey());
        assertNotEquals("updated-key-0987654321", storedApiKey(channelId));
    }

    @Test
    @DisplayName("更新渠道：不存在时报 CHANNEL_NOT_EXISTS")
    public void updateChannelNotExists() {
        ChannelUpdateCommand command = new ChannelUpdateCommand();
        command.setId(999L);
        command.setName("渠道");
        command.setProvider(ChannelProvider.OPENAI.getCode());
        command.setBaseUrl("https://api.openai.com/v1");

        assertServiceException(() -> channelController.updateChannel(command), CHANNEL_NOT_EXISTS);
    }

    @Test
    @DisplayName("启停渠道：停用后查询过滤生效，可再启用")
    public void updateChannelStatus() {
        Long channelId = channelController.createChannel(createCommand()).getData();

        ChannelUpdateStatusCommand disable = new ChannelUpdateStatusCommand();
        disable.setId(channelId);
        disable.setEnabled(false);
        assertEquals(SUCCESS.getCode(), channelController.updateChannelStatus(disable).getCode());
        assertFalse(channelMapper.selectById(channelId).getEnabled());

        ChannelPageQuery query = new ChannelPageQuery();
        query.setEnabled(true);
        assertEquals(0, channelController.getChannelPage(query).getData().getTotal());

        ChannelUpdateStatusCommand enable = new ChannelUpdateStatusCommand();
        enable.setId(channelId);
        enable.setEnabled(true);
        channelController.updateChannelStatus(enable);
        assertTrue(channelMapper.selectById(channelId).getEnabled());
    }

    @Test
    @DisplayName("删除渠道：逻辑删除后详情与分页均不可见")
    public void deleteChannel() {
        Long channelId = channelController.createChannel(createCommand()).getData();

        assertEquals(SUCCESS.getCode(), channelController.deleteChannel(channelId).getCode());

        assertNull(channelMapper.selectById(channelId));
        assertServiceException(() -> channelController.getChannel(channelId), CHANNEL_NOT_EXISTS);
        assertEquals(0, channelController.getChannelPage(new ChannelPageQuery()).getData().getTotal());
    }

    @Test
    @DisplayName("分页查询：按名称/提供商/启用状态过滤")
    public void getChannelPageWithConditions() {
        channelController.createChannel(createCommand()).getData();

        ChannelCreateCommand compatCommand = createCommand();
        compatCommand.setName("中转渠道");
        compatCommand.setProvider(ChannelProvider.OPENAI_COMPAT.getCode());
        compatCommand.setBaseUrl("https://relay.example.com/v1");
        channelController.createChannel(compatCommand);

        ChannelPageQuery byName = new ChannelPageQuery();
        byName.setName("中转");
        assertEquals(1, channelController.getChannelPage(byName).getData().getTotal());

        ChannelPageQuery byProvider = new ChannelPageQuery();
        byProvider.setProvider(ChannelProvider.OPENAI.getCode());
        assertEquals(1, channelController.getChannelPage(byProvider).getData().getTotal());

        ChannelPageQuery byEnabled = new ChannelPageQuery();
        byEnabled.setEnabled(true);
        assertEquals(2, channelController.getChannelPage(byEnabled).getData().getTotal());
    }

    @Test
    @DisplayName("渠道详情：DTO 携带脱敏密钥，不暴露明文")
    public void getChannelDetail() {
        Long channelId = channelController.createChannel(createCommand()).getData();

        ChannelDTO dto = channelController.getChannel(channelId).getData();

        assertEquals(channelId, dto.getId());
        assertEquals("OpenAI 主渠道", dto.getName());
        assertEquals(ChannelProvider.OPENAI.getCode(), dto.getProvider());
        assertEquals("https://api.openai.com/v1", dto.getBaseUrl());
        assertEquals("test****7890", dto.getApiKeyMasked());
        assertEquals(true, dto.getEnabled());
        assertEquals("tenant", dto.getOwnerType());
        assertNotNull(dto.getCreateTime());
    }

    @Test
    @DisplayName("分页查询：DTO 密钥脱敏，不暴露明文")
    public void getChannelPageMasksApiKey() {
        channelController.createChannel(createCommand()).getData();

        PageResult<ChannelDTO> page = channelController.getChannelPage(new ChannelPageQuery()).getData();

        assertEquals(1, page.getTotal());
        assertEquals("test****7890", page.getList().get(0).getApiKeyMasked());
    }

    @Test
    @DisplayName("租户隔离：租户 1 的渠道对租户 2 不可见，切回后恢复可见")
    public void tenantIsolation() {
        Long channelId = channelController.createChannel(createCommand()).getData();

        TenantContextHolder.setTenantId(2L);
        assertEquals(0, channelController.getChannelPage(new ChannelPageQuery()).getData().getTotal());
        assertServiceException(() -> channelController.getChannel(channelId), CHANNEL_NOT_EXISTS);

        TenantContextHolder.setTenantId(1L);
        assertEquals(1, channelController.getChannelPage(new ChannelPageQuery()).getData().getTotal());
    }

}
