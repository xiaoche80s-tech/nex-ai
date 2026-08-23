package com.gkht.ai.nexai.module.ai.channel.interfaces.controller.admin.channel;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelConnectivityTestCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ChannelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ChannelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ChannelPageQuery;
import com.gkht.ai.nexai.module.ai.channel.application.service.ChannelService;
import com.gkht.ai.nexai.module.ai.channel.application.service.ChannelServiceImpl;
import com.gkht.ai.nexai.module.ai.channel.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ConnectivityResult;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ChannelConverterImpl;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.converter.ModelConverterImpl;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ModelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.repository.ChannelRepositoryImpl;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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
 * 渠道 HTTP 契约测试（H2 全链路）：CRUD、密钥加密落库与脱敏出参、探测编排（stub 网关）、
 * 多租户隔离。探测的真实外呼行为由网关实现侧保障，此处只测端口编排。
 */
@Import({ChannelController.class, ChannelServiceImpl.class, ChannelRepositoryImpl.class,
        ChannelConverterImpl.class, ModelConverterImpl.class, ModelMapper.class,
        TenantDbTestConfiguration.class, ChannelControllerTest.FakeGatewayConfiguration.class})
public class ChannelControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;
    private static final long TENANT_TWO = 2L;

    @Resource
    private ChannelController channelController;

    @Resource
    private ChannelService channelService;

    @Resource
    private ChannelMapper channelMapper;

    @Resource
    private DataSource dataSource;

    /** 注入以强制初始化租户拦截器 bean（懒加载环境下不被依赖则不初始化，Mapper 将无租户过滤） */
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;

    /** 探测网关桩：记录探测目标并返回固定结果（不外呼） */
    @TestConfiguration
    static class FakeGatewayConfiguration {

        @Bean
        public ModelConnectivityGateway modelConnectivityGateway() {
            return new ModelConnectivityGateway() {
                @Override
                public ConnectivityResult probe(Channel channel, String modelId) {
                    return ConnectivityResult.success(42L, "stub-ok（探测目标："
                            + channel.getBaseUrl() + " / " + modelId + " / 密钥=" + channel.getApiKey() + "）");
                }
            };
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

    private ChannelCreateCommand createCommand(String name) {
        ChannelCreateCommand command = new ChannelCreateCommand();
        command.setName(name);
        command.setProvider("openai");
        command.setBaseUrl("https://api.openai.com/v1");
        command.setApiKey("sk-live-1234567890abcdef");
        return command;
    }

    /** 落库断言：密钥密文落库（参数化查询，先设参后执行） */
    private String queryStoredApiKey(Long id, long tenantId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT api_key FROM ai_channel WHERE id = ? AND tenant_id = ?")) {
            statement.setLong(1, id);
            statement.setLong(2, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    @Test
    @DisplayName("创建渠道：密钥 AES 密文落库、出参脱敏、归属固定租户侧、初始启用")
    public void createChannelPersistsEncryptedKey() throws SQLException {
        Long id = channelController.createChannel(createCommand("OpenAI 主渠道")).getData();
        assertNotNull(id);

        String stored = queryStoredApiKey(id, TENANT_ONE);
        assertNotNull(stored);
        assertNotEquals("sk-live-1234567890abcdef", stored, "密钥必须密文落库");
        assertFalse(stored.contains("sk-live"), "密文不得含明文片段");

        ChannelDTO dto = channelController.getChannel(id).getData();
        assertEquals("sk-l****cdef", dto.getApiKeyMasked());
        assertTrue(dto.getApiKeyConfigured());
        assertEquals("tenant", dto.getOwnerType());
        assertTrue(dto.getEnabled());
    }

    @Test
    @DisplayName("更新渠道：密钥留空保留原密钥；传新密钥覆盖；本地服务密钥可为空")
    public void updateKeepsOrOverwritesApiKey() {
        Long id = channelController.createChannel(createCommand("主渠道")).getData();

        ChannelUpdateCommand keepKey = new ChannelUpdateCommand();
        keepKey.setId(id);
        keepKey.setName("改名");
        keepKey.setProvider("openai");
        keepKey.setBaseUrl("https://api.openai.com/v1");
        keepKey.setApiKey(null);
        channelController.updateChannel(keepKey).getData();
        assertEquals("sk-l****cdef", channelController.getChannel(id).getData().getApiKeyMasked());

        ChannelUpdateCommand newKey = new ChannelUpdateCommand();
        newKey.setId(id);
        newKey.setName("改名");
        newKey.setProvider("openai");
        newKey.setBaseUrl("https://api.openai.com/v1");
        newKey.setApiKey("sk-new-987654321zyxwvu");
        channelController.updateChannel(newKey).getData();
        assertEquals("sk-n****xwvu", channelController.getChannel(id).getData().getApiKeyMasked());

        // 空白同样表示保留原密钥（密钥只能保留或更换，不存在清除）
        ChannelUpdateCommand blankKeeps = new ChannelUpdateCommand();
        blankKeeps.setId(id);
        blankKeeps.setName("Ollama 改造");
        blankKeeps.setProvider("ollama");
        blankKeeps.setBaseUrl("http://localhost:11434");
        blankKeeps.setApiKey("  ");
        channelController.updateChannel(blankKeeps).getData();
        assertEquals("sk-n****xwvu", channelController.getChannel(id).getData().getApiKeyMasked());
    }

    @Test
    @DisplayName("启停与删除：停用保留数据；删除渠道不存在时报业务错误码")
    public void statusAndDelete() {
        Long id = channelController.createChannel(createCommand("渠道")).getData();

        ChannelUpdateStatusCommand disable = new ChannelUpdateStatusCommand();
        disable.setId(id);
        disable.setEnabled(false);
        channelController.updateChannelStatus(disable);
        assertEquals(false, channelController.getChannel(id).getData().getEnabled());

        channelController.deleteChannel(id);
        assertServiceException(() -> channelController.getChannel(id), CHANNEL_NOT_EXISTS);
    }

    @Test
    @DisplayName("提供商编码非法被拒绝（业务错误码而非 500）")
    public void rejectsInvalidProvider() {
        ChannelCreateCommand command = createCommand("坏渠道");
        command.setProvider("not-a-provider");
        assertServiceException(() -> channelService.createChannel(command), CHANNEL_PROVIDER_INVALID);
    }

    @Test
    @DisplayName("连通性探测：表单凭据即测不落库；携带 channelId 且密钥留空时回退已存密钥")
    public void connectivityTestFallsBackToStoredKey() {
        Long id = channelController.createChannel(createCommand("已存渠道")).getData();

        // 表单即测：直接用命令里的凭据
        ChannelConnectivityTestCommand formProbe = new ChannelConnectivityTestCommand();
        formProbe.setProvider("openai");
        formProbe.setBaseUrl("https://form.example.com/v1");
        formProbe.setApiKey("sk-form-aaaabbbbcccc");
        formProbe.setModelId("gpt-4o");
        ConnectivityTestDTO formResult = channelController.testChannelConnectivity(formProbe).getData();
        assertTrue(formResult.getSuccess());
        assertTrue(formResult.getMessage().contains("https://form.example.com/v1"));
        assertTrue(formResult.getMessage().contains("sk-form-aaaabbbbcccc"));

        // 编辑已存渠道密钥留空：回退已存密钥
        ChannelConnectivityTestCommand fallbackProbe = new ChannelConnectivityTestCommand();
        fallbackProbe.setChannelId(id);
        fallbackProbe.setProvider("openai");
        fallbackProbe.setBaseUrl("https://api.openai.com/v1");
        fallbackProbe.setApiKey("  ");
        fallbackProbe.setModelId("gpt-4o");
        ConnectivityTestDTO fallbackResult = channelController.testChannelConnectivity(fallbackProbe).getData();
        assertTrue(fallbackResult.getMessage().contains("sk-live-1234567890abcdef"), "应回退已存密钥探测");
    }

    @Test
    @DisplayName("多租户隔离：租户二查不到租户一的渠道；同名渠道在租户二可独立创建")
    public void tenantIsolation() {
        channelController.createChannel(createCommand("租户一渠道"));

        TenantContextHolder.setTenantId(TENANT_TWO);
        PageResult<ChannelDTO> otherTenant = channelController.getChannelPage(new ChannelPageQuery()).getData();
        assertEquals(0, otherTenant.getTotal());

        channelController.createChannel(createCommand("租户一渠道"));
        assertEquals(1, channelController.getChannelPage(new ChannelPageQuery()).getData().getTotal());
    }

}
