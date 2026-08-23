package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository.AgentSpecRepositoryImpl;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CODE_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CONFIG_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能体规格 HTTP 契约测试（H2 全链路）：直接调用 /admin-api/ai/spec/** 对应的控制器方法，
 * 走真实 controller → application → repository → H2 链路，只断言外部行为（落库 JSON、
 * 错误码、租户隔离与可见性口径），不测实现细节。
 */
@Import({AgentSpecController.class, AgentSpecServiceImpl.class, AgentSpecRepositoryImpl.class,
        AgentSpecConverterImpl.class, AgentSpecControllerTest.TenantDbTestConfiguration.class})
public class AgentSpecControllerTest extends BaseDbUnitTest {

    /** 测试租户一号 / 二号（隔离断言用） */
    private static final long TENANT_ONE = 1L;
    private static final long TENANT_TWO = 2L;

    @Resource
    private AgentSpecController agentSpecController;

    @Resource
    private AgentSpecService agentSpecService;

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private DataSource dataSource;

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

    private AgentSpecCreateCommand createCommand(String specCode) {
        AgentSpecCreateCommand command = new AgentSpecCreateCommand();
        command.setName("客服助手");
        command.setSpecCode(specCode);
        command.setDescription("回答客户咨询");
        command.setIcon("ep:service");
        command.setSystemPrompt("你是企业的智能客服");
        command.setMaxIters(10);
        command.setTemperature(0.7d);
        return command;
    }

    /** 落库断言：草稿 JSON 内容（参数化查询，按业务编码 + 测试租户一号） */
    private String queryDraft(String specCode) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT draft FROM ai_agent_spec WHERE spec_code = ? AND tenant_id = ?")) {
            statement.setString(1, specCode);
            statement.setLong(2, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    /** 落库断言：归属层级落库值 */
    private String queryOwnerLevel(String specCode) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT owner_level FROM ai_agent_spec WHERE spec_code = ? AND tenant_id = ?")) {
            statement.setString(1, specCode);
            statement.setLong(2, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    @Test
    @DisplayName("创建规格：草稿 JSON 按四层结构落库、租户自动归属、默认租户级")
    public void createSpecPersistsDraft() throws SQLException {
        Long id = agentSpecController.createSpec(createCommand("customer-service")).getData();
        assertNotNull(id);

        String draft = queryDraft("customer-service");
        assertNotNull(draft);
        assertTrue(draft.contains("\"description\":\"回答客户咨询\""), "草稿 JSON 应含自描述");
        assertTrue(draft.contains("\"systemPrompt\":\"你是企业的智能客服\""), "草稿 JSON 应含系统提示");
        assertTrue(draft.contains("\"maxIters\":10"), "草稿 JSON 应含迭代上限");
        assertTrue(draft.contains("\"temperature\":0.7"), "草稿 JSON 应含调用参数");
        assertTrue(draft.contains("\"generateOptions\""), "草稿 JSON 应含模型调用层分组");
        assertFalse(draft.contains("\"executionEnv\""), "纯对话默认不应产生执行环境层");
        assertEquals("TENANT", queryOwnerLevel("customer-service"));
    }

    @Test
    @DisplayName("用户级归属：归属用户取当前登录态而非前端传入")
    public void userLevelBindsToLoginUser() {
        AgentSpecCreateCommand command = createCommand("my-assistant");
        command.setOwnerLevel("USER");
        // Controller 无登录态（getLoginUserId 为 null 的兜底口径）：创建被拒（用户级必须登录）
        assertServiceException(() -> agentSpecController.createSpec(command),
                AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED);

        // 服务层显式传登录用户：归属用户落库，同登录用户可见
        agentSpecService.createSpec(command, 100L);
        PageResult<AgentSpecDTO> asUser = agentSpecService.getSpecPage(new AgentSpecPageQuery(), 100L);
        assertEquals(1, asUser.getTotal());
        assertEquals("my-assistant", asUser.getList().get(0).getSpecCode());
        assertEquals(100L, asUser.getList().get(0).getOwnerUserId());
        assertTrue(asUser.getList().get(0).getHasDraft());
        assertEquals("回答客户咨询", asUser.getList().get(0).getDescription());
    }

    @Test
    @DisplayName("用户级缺登录态被拒绝（归属用户取当前登录态）")
    public void userLevelRequiresLogin() {
        AgentSpecCreateCommand command = createCommand("no-login");
        command.setOwnerLevel("USER");
        assertServiceException(() -> agentSpecService.createSpec(command, null),
                AGENT_SPEC_USER_OWNER_LOGIN_REQUIRED);
    }

    @Test
    @DisplayName("归属层级内唯一：同层级重复业务编码被拒绝，跨层级同编码可共存")
    public void specCodeUniqueWithinOwnerLevel() {
        AgentSpecCreateCommand tenantLevel = createCommand("shared-code");
        agentSpecController.createSpec(tenantLevel).getData();

        assertServiceException(() -> agentSpecController.createSpec(createCommand("shared-code")),
                AGENT_SPEC_CODE_DUPLICATE, "shared-code");

        AgentSpecCreateCommand userLevel = createCommand("shared-code");
        userLevel.setOwnerLevel("USER");
        agentSpecService.createSpec(userLevel, 100L);
        assertTrue(agentSpecMapper.existsBySpecCode(OwnerLevel.TENANT, null, "shared-code"));
        assertTrue(agentSpecMapper.existsBySpecCode(OwnerLevel.USER, 100L, "shared-code"));
    }

    @Test
    @DisplayName("平台级归属被拒绝（MVP 仅开放租户级/用户级）")
    public void platformLevelRejected() {
        AgentSpecCreateCommand command = createCommand("platform-spec");
        command.setOwnerLevel("PLATFORM");
        assertServiceException(() -> agentSpecService.createSpec(command, null),
                AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED);
    }

    @Test
    @DisplayName("执行环境校验链：未启用 workspace 时开启沙箱被拒绝（业务错误码而非 500）")
    public void rejectsSandboxWithoutWorkspace() {
        AgentSpecCreateCommand command = createCommand("bad-env");
        command.setSandboxEnabled(true);
        assertServiceException(() -> agentSpecService.createSpec(command, null),
                AGENT_SPEC_CONFIG_INVALID, "未启用 workspace 时不能开启沙箱或执行能力（纯对话智能体）");
    }

    @Test
    @DisplayName("多租户隔离：租户二查不到租户一的规格，同编码在租户二可独立创建")
    public void tenantIsolation() {
        agentSpecController.createSpec(createCommand("tenant-one-spec")).getData();

        TenantContextHolder.setTenantId(TENANT_TWO);
        PageResult<AgentSpecDTO> otherTenant = agentSpecController.getSpecPage(new AgentSpecPageQuery()).getData();
        assertEquals(0, otherTenant.getTotal());
        assertFalse(agentSpecMapper.existsBySpecCode(OwnerLevel.TENANT, null, "tenant-one-spec"));

        // 同一业务编码在租户二不冲突（唯一性按 归属层级+租户 维度）
        agentSpecController.createSpec(createCommand("tenant-one-spec")).getData();
        PageResult<AgentSpecDTO> tenantTwoPage = agentSpecController.getSpecPage(new AgentSpecPageQuery()).getData();
        assertEquals(1, tenantTwoPage.getTotal());
    }

    @Test
    @DisplayName("分页查询：名称模糊过滤与业务编码列/归属标签数据齐备")
    public void pageQueryWithFilters() {
        agentSpecController.createSpec(createCommand("cs-bot")).getData();
        AgentSpecCreateCommand another = createCommand("dev-bot");
        another.setName("开发助手");
        agentSpecController.createSpec(another).getData();

        PageResult<AgentSpecDTO> all = agentSpecController.getSpecPage(new AgentSpecPageQuery()).getData();
        assertEquals(2, all.getTotal());
        AgentSpecDTO first = all.getList().get(0);
        assertNotNull(first.getSpecCode(), "列表应含业务编码列");
        assertEquals("TENANT", first.getOwnerLevel(), "列表应含归属标签数据");

        AgentSpecPageQuery byName = new AgentSpecPageQuery();
        byName.setName("客服");
        assertEquals(1, agentSpecController.getSpecPage(byName).getData().getTotal());

        AgentSpecPageQuery byCode = new AgentSpecPageQuery();
        byCode.setSpecCode("dev-bot");
        assertEquals(1, agentSpecController.getSpecPage(byCode).getData().getTotal());
    }

}
