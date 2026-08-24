package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.system.api.user.AdminUserApi;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CODE_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CONFIG_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_PUBLISH_INVALID;
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

    /** 发布人昵称解析走 system api（跨模块），单测上下文无 system 模块，mock 之（工单 25） */
    @MockitoBean
    private AdminUserApi adminUserApi;

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

    /** 落库断言：版本快照 config JSON（按规格编号 + 版本号 + 测试租户一号） */
    private String queryVersionConfig(Long specId, int versionNo) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT config FROM ai_agent_spec_version WHERE spec_id = ? AND version_no = ? AND tenant_id = ?")) {
            statement.setLong(1, specId);
            statement.setInt(2, versionNo);
            statement.setLong(3, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private AgentSpecUpdateCommand updateCommand(Long id) {
        AgentSpecUpdateCommand command = new AgentSpecUpdateCommand();
        command.setId(id);
        command.setName("客服助手");
        command.setDescription("回答客户咨询");
        command.setSystemPrompt("你是企业的智能客服");
        command.setMaxIters(10);
        command.setTemperature(0.7d);
        return command;
    }

    private AgentSpecPublishCommand publishCommand(Long id) {
        AgentSpecPublishCommand command = new AgentSpecPublishCommand();
        command.setId(id);
        return command;
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
    @DisplayName("folders 通道（工单 18）：清单随草稿落库；未启用 workspace 挂载被拒")
    public void foldersMountPersistsAndValidates() throws SQLException {
        AgentSpecCreateCommand command = createCommand("folder-spec");
        command.setWorkspaceEnabled(true);
        command.setFolders(List.of(folderCommand("faq")));

        agentSpecController.createSpec(command).getData();
        String draft = queryDraft("folder-spec");
        assertNotNull(draft);
        assertTrue(draft.contains("\"folders\""), "草稿 JSON 应含文件夹清单分组");
        assertTrue(draft.contains("\"type\":\"ASSET\""), "草稿应固化文件夹类型");
        assertTrue(draft.contains("\"contentHash\":\"" + "a".repeat(64) + "\""),
                "草稿应固化内容哈希（内容寻址）");
        assertTrue(draft.contains("\"executionEnv\""), "挂载文件夹的草稿应含执行环境层");

        // 未启用 workspace 挂载文件夹 → 领域校验拒绝（业务错误码而非 500）
        AgentSpecCreateCommand noWorkspace = createCommand("folder-nows");
        noWorkspace.setFolders(List.of(folderCommand("faq")));
        assertServiceException(() -> agentSpecService.createSpec(noWorkspace, null),
                AGENT_SPEC_CONFIG_INVALID, "挂载私有文件夹须启用 workspace（文件物化落 workspace）");
    }

    @Test
    @DisplayName("编辑面：更新整体替换草稿、详情平铺回填；补齐模型引用后发布走通")
    public void updateReplacesDraftAndUnblocksPublish() throws SQLException {
        Long id = agentSpecController.createSpec(createCommand("edit-spec")).getData();

        // 缺模型引用时发布被拒（发布校验补齐草稿态可空配置）
        assertServiceException(() -> agentSpecService.publishSpec(publishCommand(id)),
                AGENT_SPEC_PUBLISH_INVALID, "发布前必须为规格配置模型");

        // 编辑补齐模型引用并修改系统提示
        AgentSpecUpdateCommand update = updateCommand(id);
        update.setModelId(7L);
        update.setSystemPrompt("你是翻译助手");
        agentSpecController.updateSpec(update);

        AgentSpecDetailDTO detail = agentSpecController.getSpec(id).getData();
        assertEquals(7L, detail.getModelId());
        assertEquals("你是翻译助手", detail.getSystemPrompt());
        assertEquals(0.7d, detail.getTemperature(), "调用参数应平铺回填");
        assertEquals("edit-spec", detail.getSpecCode());
        assertEquals("TENANT", detail.getOwnerLevel());
        assertTrue(detail.getHasDraft());
        String draft = queryDraft("edit-spec");
        assertTrue(draft.contains("\"modelId\":7"), "草稿 JSON 应含模型引用");

        // 补齐模型后发布成功
        assertEquals(1, agentSpecService.publishSpec(publishCommand(id)));
    }

    @Test
    @DisplayName("编辑面：发布后编辑只动草稿，已发布快照与当前版本指针不受影响")
    public void editingAfterPublishDoesNotMutateSnapshot() throws SQLException {
        Long id = agentSpecController.createSpec(createCommand("edit-snapshot")).getData();
        AgentSpecUpdateCommand withModel = updateCommand(id);
        withModel.setModelId(7L);
        agentSpecController.updateSpec(withModel);
        agentSpecService.publishSpec(publishCommand(id));
        String frozen = queryVersionConfig(id, 1);
        assertTrue(frozen.contains("你是企业的智能客服"));

        AgentSpecUpdateCommand edited = updateCommand(id);
        edited.setModelId(7L);
        edited.setSystemPrompt("新提示");
        agentSpecController.updateSpec(edited);
        assertEquals(frozen, queryVersionConfig(id, 1), "v1 快照不得因后续编辑而变化");

        AgentSpecDetailDTO detail = agentSpecController.getSpec(id).getData();
        assertEquals("新提示", detail.getSystemPrompt());
        assertEquals(1, detail.getCurrentVersionNo(), "编辑草稿不得改变当前版本指针");
    }

    @Test
    @DisplayName("编辑/详情：不存在的规格报业务错误码")
    public void updateOrGetMissingSpecRejected() {
        assertServiceException(() -> agentSpecController.getSpec(9999L), AGENT_SPEC_NOT_EXISTS);
        assertServiceException(() -> agentSpecController.updateSpec(updateCommand(9999L)),
                AGENT_SPEC_NOT_EXISTS);
    }

    /** 文件夹挂载命令（固定凭证：faq.md + 全 a 哈希） */
    private com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand
    folderCommand(String name) {
        com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand.FolderFileCommand file =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand.FolderFileCommand();
        file.setPath("faq.md");
        file.setUrl("http://files/faq.md");
        file.setContentHash("a".repeat(64));
        file.setSize(12L);
        com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand folder =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand();
        folder.setType("ASSET");
        folder.setName(name);
        folder.setFiles(List.of(file));
        return folder;
    }

    @Test
    @DisplayName("发布后分页列表回传当前版本号（列表状态列渲染依据）")
    public void pageQueryCarriesCurrentVersionNo() {
        Long id = agentSpecController.createSpec(createCommand("published-spec")).getData();
        AgentSpecUpdateCommand withModel = updateCommand(id);
        withModel.setModelId(7L);
        agentSpecController.updateSpec(withModel);
        agentSpecService.publishSpec(publishCommand(id));

        PageResult<AgentSpecDTO> page = agentSpecController.getSpecPage(new AgentSpecPageQuery()).getData();
        assertEquals(1, page.getTotal());
        assertNotNull(page.getList().get(0).getCurrentVersionNo(), "未发布前列表不返回版本号则为草稿态");
        assertEquals(1, page.getList().get(0).getCurrentVersionNo(), "发布后列表应回传当前版本号供状态列渲染");
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
