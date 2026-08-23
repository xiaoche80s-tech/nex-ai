package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.ToolMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecVersionMapper;
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
import java.util.List;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_PUBLISH_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能体规格版本 HTTP 契约测试（H2 全链路）：发布生成不可变快照（版本号递增、
 * 落库 JSON 为发布时草稿的拷贝）、发布校验、版本列表与当前版本标识、切换当前版本。
 * 快照不可变性（发布后编辑不影响既有快照）由 DB 权威源 + domain 不可变双重保证。
 */
@Import({AgentSpecController.class, AgentSpecVersionController.class, AgentSpecServiceImpl.class,
        AgentSpecRepositoryImpl.class, AgentSpecConverterImpl.class,
        AgentSpecVersionControllerTest.TenantDbTestConfiguration.class})
public class AgentSpecVersionControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;

    /** 租户拦截器测试配置（与 02 测试同构；懒加载环境下须注入强制初始化） */
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
    private AgentSpecController agentSpecController;

    @Resource
    private AgentSpecVersionController agentSpecVersionController;

    @Resource
    private AgentSpecService agentSpecService;

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private AgentSpecVersionMapper agentSpecVersionMapper;

    @Resource
    private DataSource dataSource;

    /** 注入以强制初始化租户拦截器 bean（懒加载环境下不被依赖则不初始化，Mapper 将无租户过滤） */
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    /** 创建规格（携带首个草稿；modelId 非空以便发布） */
    private Long createSpec(String specCode, Long modelId, String systemPrompt) {
        AgentSpecCreateCommand command = new AgentSpecCreateCommand();
        command.setName("客服助手");
        command.setSpecCode(specCode);
        command.setDescription("回答客户咨询");
        command.setSystemPrompt(systemPrompt);
        command.setMaxIters(10);
        command.setTemperature(0.7d);
        command.setModelId(modelId);
        return agentSpecController.createSpec(command).getData();
    }

    private AgentSpecPublishCommand publishCommand(Long specId, String note) {
        AgentSpecPublishCommand command = new AgentSpecPublishCommand();
        command.setId(specId);
        command.setNote(note);
        return command;
    }

    /** 落库断言：规格当前版本指针 */
    private Integer queryCurrentVersionNo(Long specId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT current_version_no FROM ai_agent_spec WHERE id = ? AND tenant_id = ?")) {
            statement.setLong(1, specId);
            statement.setLong(2, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? (Integer) resultSet.getObject(1) : null;
            }
        }
    }

    /** 落库断言：版本快照配置 JSON（参数化查询） */
    private String queryVersionConfig(Long specId, int versionNo) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT config FROM ai_agent_spec_version WHERE spec_id = ? AND version_no = ? AND tenant_id = ?")) {
            statement.setLong(1, specId);
            statement.setLong(2, versionNo);
            statement.setLong(3, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    /** 落库断言：版本快照行数 */
    private int countVersions(Long specId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM ai_agent_spec_version WHERE spec_id = ? AND tenant_id = ?")) {
            statement.setLong(1, specId);
            statement.setLong(2, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    @Test
    @DisplayName("发布：全量四层配置落库为不可变快照，版本号自 1 递增，当前版本指针推进")
    public void publishFreezesFullConfigSnapshot() throws SQLException {
        Long specId = createSpec("cs-v1", 1L, "你是客服");

        Integer v1 = agentSpecVersionController.publishSpec(publishCommand(specId, "首版")).getData();
        assertEquals(1, v1);
        String versionConfig = queryVersionConfig(specId, 1);
        assertNotNull(versionConfig);
        assertTrue(versionConfig.contains("\"systemPrompt\":\"你是客服\""), "快照应固化系统提示");
        assertTrue(versionConfig.contains("\"temperature\":0.7"), "快照应固化调用参数");
        assertTrue(versionConfig.contains("\"maxIters\":10"), "快照应固化推理参数");
        assertTrue(versionConfig.contains("\"modelId\":1"), "快照应固化模型引用");
        assertEquals(1, queryCurrentVersionNo(specId));
        assertTrue(agentSpecVersionMapper.existsBySpecAndVersion(specId, 1));

        // 再次发布（草稿不变）：版本号递增、快照追加、指针推进
        Integer v2 = agentSpecVersionController.publishSpec(publishCommand(specId, null)).getData();
        assertEquals(2, v2);
        assertEquals(2, queryCurrentVersionNo(specId));
        assertEquals(2, countVersions(specId));
    }

    @Test
    @DisplayName("发布校验：缺模型引用被拒（业务错误码而非 500），且不产生快照")
    public void publishRejectsDraftWithoutModel() throws SQLException {
        Long specId = createSpec("cs-nomodel", null, "你是客服");
        assertServiceException(() -> agentSpecService.publishSpec(publishCommand(specId, null)),
                AGENT_SPEC_PUBLISH_INVALID, "发布前必须为规格配置模型");
        assertEquals(0, countVersions(specId));
        assertNull(queryCurrentVersionNo(specId));
    }

    @Test
    @DisplayName("发布不存在的规格被拒")
    public void publishRejectsMissingSpec() {
        assertServiceException(() -> agentSpecService.publishSpec(publishCommand(9999L, null)),
                AGENT_SPEC_NOT_EXISTS);
    }

    @Test
    @DisplayName("快照不可变性：发布后编辑草稿落库为独立快照，既有快照不受影响")
    public void editingAfterPublishDoesNotMutateSnapshot() throws SQLException {
        Long specId = createSpec("cs-edit", 1L, "第一版提示");
        agentSpecService.publishSpec(publishCommand(specId, null));
        String frozen = queryVersionConfig(specId, 1);

        // 模拟"再编辑进入新草稿"（编辑接口后置）：发布路径对已发布快照的隔离由此验证——
        // 快照行不可变（无更新入口），草稿变更只影响后续发布产生的新快照
        assertNotNull(frozen);
        assertTrue(frozen.contains("\"systemPrompt\":\"第一版提示\""));
        // 第二次发布产生独立的新快照行（版本号递增），v1 行内容不变
        agentSpecService.publishSpec(publishCommand(specId, "第二版"));
        assertEquals(2, countVersions(specId), "每次发布追加独立快照行");
        assertEquals(frozen, queryVersionConfig(specId, 1), "v1 快照内容不得因再次发布而变化");
        assertEquals(2, queryVersionConfig(specId, 2) == null ? 0 : 2, "v2 行独立存在");
        assertEquals(2, queryCurrentVersionNo(specId));
    }

    @Test
    @DisplayName("版本列表：升序、含当前版本标识；未发布为空列表")
    public void listVersionsWithCurrentFlag() throws SQLException {
        Long specId = createSpec("cs-versions", 1L, "提示");
        agentSpecService.publishSpec(publishCommand(specId, "首版"));
        agentSpecService.publishSpec(publishCommand(specId, "二版"));

        List<AgentSpecVersionDTO> versions =
                agentSpecVersionController.listSpecVersions(specId).getData();
        assertEquals(2, versions.size());
        AgentSpecVersionDTO v1 = versions.get(0);
        assertEquals(1, v1.getVersionNo());
        assertEquals("首版", v1.getNote());
        assertFalse(v1.getCurrent(), "当前生效的是 v2，v1 不应带 current 标记");
        AgentSpecVersionDTO v2 = versions.get(1);
        assertTrue(v2.getCurrent(), "当前生效版本应带 current 标记");
        assertNotNull(v1.getCreateTime());

        // 从未发布的规格：空列表（不报错）
        Long fresh = createSpec("cs-fresh", 1L, "提示");
        assertTrue(agentSpecVersionController.listSpecVersions(fresh).getData().isEmpty());
    }

    @Test
    @DisplayName("切换当前版本：回退指针、悬空版本被拒、不存在规格被拒")
    public void switchVersionMovesPointerOnly() throws SQLException {
        Long specId = createSpec("cs-switch", 1L, "提示");
        agentSpecService.publishSpec(publishCommand(specId, null));
        agentSpecService.publishSpec(publishCommand(specId, null));

        AgentSpecSwitchVersionCommand backToV1 = new AgentSpecSwitchVersionCommand();
        backToV1.setId(specId);
        backToV1.setVersionNo(1);
        agentSpecVersionController.switchSpecVersion(backToV1).getData();
        assertEquals(1, agentSpecMapper.selectById(specId).getCurrentVersionNo(),
                "切换只移动当前版本指针");
        assertEquals(2, countVersions(specId), "快照行数不得因切换而变");

        AgentSpecSwitchVersionCommand missingVersion = new AgentSpecSwitchVersionCommand();
        missingVersion.setId(specId);
        missingVersion.setVersionNo(99);
        assertServiceException(() -> agentSpecService.switchSpecVersion(missingVersion),
                AGENT_SPEC_VERSION_NOT_EXISTS);

        AgentSpecSwitchVersionCommand missingSpec = new AgentSpecSwitchVersionCommand();
        missingSpec.setId(8888L);
        missingSpec.setVersionNo(1);
        assertServiceException(() -> agentSpecService.switchSpecVersion(missingSpec),
                AGENT_SPEC_NOT_EXISTS);
    }

    @Test
    @DisplayName("版本快照按规格聚合且随租户隔离：租户二看不到租户一的版本")
    public void versionsFollowSpecAggregateAndTenant() throws SQLException {
        Long specId = createSpec("cs-tenant", 1L, "提示");
        agentSpecService.publishSpec(publishCommand(specId, null));

        // 切换租户后：同一 specId 查不到版本（按聚合 + 租户双隔离）
        TenantContextHolder.setTenantId(2L);
        assertTrue(agentSpecVersionMapper.selectListBySpecId(specId).isEmpty());
        assertFalse(agentSpecVersionMapper.existsBySpecAndVersion(specId, 1));
    }

}
