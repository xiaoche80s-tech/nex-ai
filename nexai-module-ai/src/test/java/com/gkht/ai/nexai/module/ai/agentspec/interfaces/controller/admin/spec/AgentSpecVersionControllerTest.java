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
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.ToolMountCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecVersionMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository.AgentSpecRepositoryImpl;
import com.gkht.ai.nexai.module.system.api.user.AdminUserApi;
import com.gkht.ai.nexai.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_PUBLISH_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASSEMBLE_INVALID;
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

    /** 发布人昵称解析走 system api（跨模块），单测上下文无 system 模块，mock 之 */
    @MockitoBean
    private AdminUserApi adminUserApi;

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

    /** 编辑保存命令（重建草稿——发布清空后回到草稿态的唯一入口） */
    private AgentSpecUpdateCommand updateCommand(Long specId, String systemPrompt) {
        AgentSpecUpdateCommand command = new AgentSpecUpdateCommand();
        command.setId(specId);
        command.setName("客服助手");
        command.setDescription("回答客户咨询");
        command.setSystemPrompt(systemPrompt);
        command.setMaxIters(10);
        command.setTemperature(0.7d);
        command.setModelId(1L);
        return command;
    }

    /** 落库断言：规格草稿 JSON（NULL 表示发布后已清空） */
    private String queryDraftById(Long specId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT draft FROM ai_agent_spec WHERE id = ? AND tenant_id = ?")) {
            statement.setLong(1, specId);
            statement.setLong(2, TENANT_ONE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
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
    @DisplayName("发布：全量四层配置落库为不可变快照，版本号自 1 递增，当前版本指针推进，草稿清空")
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

        // 发布清空草稿（ADR 0004）：落库 draft 为 NULL，无草稿再发布被业务错误拒绝
        assertNull(queryDraftById(specId), "发布后草稿应清空");
        assertServiceException(() -> agentSpecService.publishSpec(publishCommand(specId, null)),
                AGENT_SPEC_PUBLISH_INVALID, "没有可发布的草稿");
        assertEquals(1, countVersions(specId), "被拒发布不得追加快照");
        assertEquals(1, queryCurrentVersionNo(specId));

        // 编辑保存重建草稿后再发布：版本号递增、快照追加、指针推进
        agentSpecController.updateSpec(updateCommand(specId, "第二版提示"));
        assertNotNull(queryDraftById(specId), "编辑保存应重建草稿");
        Integer v2 = agentSpecVersionController.publishSpec(publishCommand(specId, null)).getData();
        assertEquals(2, v2);
        assertEquals(2, queryCurrentVersionNo(specId));
        assertEquals(2, countVersions(specId));
        assertNull(queryDraftById(specId), "v2 发布后草稿再次清空");
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
    @DisplayName("发布固化（工单 18）：文件夹清单（url + 哈希）随版本快照固化，不可变")
    public void publishFreezesFolderManifest() throws SQLException {
        Long specId = createSpec("cs-folder", 1L, "你是客服");
        // 直接建带 folders 的草稿（复用 controller 创建面）
        com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand command =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand();
        command.setName("客服助手");
        command.setSpecCode("cs-folder-2");
        command.setSystemPrompt("你是客服");
        command.setModelId(1L);
        command.setWorkspaceEnabled(true);
        com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand.FolderFileCommand file =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand.FolderFileCommand();
        file.setPath("faq.md");
        file.setUrl("http://files/faq.md");
        file.setContentHash("a".repeat(64));
        file.setSize(12L);
        com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand folder =
                new com.gkht.ai.nexai.module.ai.agentspec.application.command.mount.FolderMountCommand();
        folder.setType("TOOLSET");
        folder.setName("scripts");
        folder.setFiles(java.util.List.of(file));
        command.setFolders(java.util.List.of(folder));
        Long folderSpecId = agentSpecController.createSpec(command).getData();

        agentSpecService.publishSpec(publishCommand(folderSpecId, null));
        String config = queryVersionConfig(folderSpecId, 1);
        assertNotNull(config);
        assertTrue(config.contains("\"folders\""), "快照应固化文件夹清单");
        assertTrue(config.contains("\"type\":\"TOOLSET\""), "快照应固化文件夹类型");
        assertTrue(config.contains("\"contentHash\":\"" + "a".repeat(64) + "\""),
                "快照应固化内容哈希（内容寻址，物化比对依据）");
        // 首个无 folders 规格的发布路径不受影响
        agentSpecService.publishSpec(publishCommand(specId, null));
        assertFalse(queryVersionConfig(specId, 1).contains("\"folders\""));
    }

    @Test
    @DisplayName("getSpec 三态（工单 22/23）：草稿态回填草稿；已发布态回填当前生效快照 + hasDraft=false；编辑保存后回到草稿态")
    public void getSpecBackfillsEffectiveSnapshotAfterPublish() throws SQLException {
        Long specId = createSpec("cs-get", 1L, "v1 提示");

        // 草稿态：平铺来自草稿
        AgentSpecDetailDTO draftDetail = agentSpecController.getSpec(specId).getData();
        assertTrue(draftDetail.getHasDraft());
        assertEquals("v1 提示", draftDetail.getSystemPrompt());

        agentSpecService.publishSpec(publishCommand(specId, "首版"));
        // 已发布态（无草稿）：平铺回填当前生效快照（编辑以此为底稿），hasDraft=false
        AgentSpecDetailDTO published = agentSpecController.getSpec(specId).getData();
        assertFalse(published.getHasDraft());
        assertEquals(1, published.getCurrentVersionNo());
        assertEquals("v1 提示", published.getSystemPrompt());
        assertEquals(1L, published.getModelId());
        assertEquals(10, published.getMaxIters());
        assertEquals(0.7d, published.getTemperature());
        assertEquals("回答客户咨询", published.getDescription());

        // 编辑保存（update）重建草稿：hasDraft 回到 true，平铺回到草稿
        agentSpecController.updateSpec(updateCommand(specId, "v2 提示"));
        AgentSpecDetailDTO editing = agentSpecController.getSpec(specId).getData();
        assertTrue(editing.getHasDraft(), "编辑保存后应回到草稿态（可再发布）");
        assertEquals("v2 提示", editing.getSystemPrompt());
        assertEquals(1, editing.getCurrentVersionNo(), "编辑保存不得改变当前版本指针");
    }

    @Test
    @DisplayName("版本快照只读预览（工单 24）：预览内容与发布时草稿逐字段一致（固化保真），后续编辑不影响")
    public void versionGetReturnsFrozenConfig() {
        Long specId = createSpec("cs-preview", 1L, "预览提示");
        agentSpecService.publishSpec(publishCommand(specId, "首版"));
        // 发布清空草稿后编辑保存新草稿：已固化快照不受影响（预览的固化保真前提）
        agentSpecController.updateSpec(updateCommand(specId, "编辑后的提示"));

        var detail = agentSpecVersionController.getSpecVersion(specId, 1).getData();
        assertEquals(1, detail.getVersionNo());
        assertEquals("首版", detail.getNote());
        assertTrue(detail.getCurrent(), "指针未切换，v1 即当前生效版本");
        assertEquals("预览提示", detail.getSystemPrompt(), "预览内容 = 发布时草稿（固化保真）");
        assertEquals(1L, detail.getModelId());
        assertEquals(10, detail.getMaxIters());
        assertEquals(0.7d, detail.getTemperature());
        assertEquals("回答客户咨询", detail.getDescription());
        assertNotNull(detail.getCreateTime());

        // 指针切换后 current 标记随指针走
        AgentSpecSwitchVersionCommand stayOnV1 = new AgentSpecSwitchVersionCommand();
        stayOnV1.setId(specId);
        stayOnV1.setVersionNo(1);
        agentSpecVersionController.switchSpecVersion(stayOnV1);
        assertTrue(agentSpecVersionController.getSpecVersion(specId, 1).getData().getCurrent());

        // 版本不存在 / 规格不存在（跨租户同此口径）→ 业务错误码
        assertServiceException(() -> agentSpecService.getSpecVersion(specId, 99),
                AGENT_SPEC_VERSION_NOT_EXISTS);
        assertServiceException(() -> agentSpecService.getSpecVersion(8888L, 1),
                AGENT_SPEC_NOT_EXISTS);
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> agentSpecService.getSpecVersion(specId, 1),
                AGENT_SPEC_NOT_EXISTS, "跨租户读取按规格 + 租户双隔离");
        TenantContextHolder.setTenantId(TENANT_ONE);
    }

    @Test
    @DisplayName("版本列表透出发布人（工单 25）：creator 批量解析昵称；用户已删除回退显示编号")
    public void listVersionsExposesPublisher() throws SQLException {
        Long specId = createSpec("cs-publisher", 1L, "提示");
        agentSpecService.publishSpec(publishCommand(specId, "首版"));
        agentSpecController.updateSpec(updateCommand(specId, "提示"));
        agentSpecService.publishSpec(publishCommand(specId, "二版"));
        // H2 无登录态（creator 由框架填 null）：SQL 直写两位发布人模拟多协作者场景
        updateCreatorByVersion(specId, 1, 1L);
        updateCreatorByVersion(specId, 2, 404L);

        AdminUserRespDTO admin = new AdminUserRespDTO();
        admin.setId(1L);
        admin.setNickname("管理员");
        when(adminUserApi.getUserMap(argThat(ids -> ids != null
                && ids.contains(1L) && ids.contains(404L))))
                .thenReturn(Map.of(1L, admin));

        List<AgentSpecVersionDTO> versions =
                agentSpecVersionController.listSpecVersions(specId).getData();
        assertEquals(2, versions.size());
        assertEquals(1L, versions.get(0).getCreator());
        assertEquals("管理员", versions.get(0).getPublisherName());
        assertEquals(404L, versions.get(1).getCreator());
        assertEquals("404", versions.get(1).getPublisherName(), "用户已删除/不存在时回退显示编号，不报错");
    }

    /** 直写版本快照发布人（模拟多协作者发布；H2 单测无登录态，审计字段不自动填充） */
    private void updateCreatorByVersion(Long specId, int versionNo, Long creator) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ai_agent_spec_version SET creator = ? WHERE spec_id = ? AND version_no = ? AND tenant_id = ?")) {
            statement.setString(1, String.valueOf(creator));
            statement.setLong(2, specId);
            statement.setInt(3, versionNo);
            statement.setLong(4, TENANT_ONE);
            statement.executeUpdate();
        }
    }

    @Test
    @DisplayName("发布不存在的规格被拒")
    public void publishRejectsMissingSpec() {
        assertServiceException(() -> agentSpecService.publishSpec(publishCommand(9999L, null)),
                AGENT_SPEC_NOT_EXISTS);
    }

    @Test
    @DisplayName("快照不可变性：发布清空草稿 → 编辑保存重建 → 再发布产生独立快照，既有快照不受影响")
    public void editingAfterPublishDoesNotMutateSnapshot() throws SQLException {
        Long specId = createSpec("cs-edit", 1L, "第一版提示");
        agentSpecService.publishSpec(publishCommand(specId, null));
        String frozen = queryVersionConfig(specId, 1);

        // 发布清空草稿；编辑保存（update）整体替换草稿（重建），不触碰已发布快照——
        // 快照行不可变（无更新入口），草稿变更只影响后续发布产生的新快照
        assertNotNull(frozen);
        assertTrue(frozen.contains("\"systemPrompt\":\"第一版提示\""));
        agentSpecController.updateSpec(updateCommand(specId, "第二版提示"));
        assertEquals(frozen, queryVersionConfig(specId, 1), "v1 快照内容不得因编辑而变化");
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
        // 发布清空草稿，编辑保存重建后方可再发布
        agentSpecController.updateSpec(updateCommand(specId, "提示"));
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
    @DisplayName("切换当前版本：回退指针且不动草稿、悬空版本被拒、不存在规格被拒")
    public void switchVersionMovesPointerOnly() throws SQLException {
        Long specId = createSpec("cs-switch", 1L, "提示");
        agentSpecService.publishSpec(publishCommand(specId, null));
        // 发布清空草稿，编辑保存重建后方可再发布
        agentSpecController.updateSpec(updateCommand(specId, "提示"));
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

    @Test
    @DisplayName("生效快照单一入口：解析出规格+当前指针版本，未发布/不存在被拒")
    public void resolveCurrentVersionSingleEntry() {
        Long specId = createSpec("cs-resolve", 1L, "提示");
        // 未发布：拒绝（运行侧不得装配未发布规格）
        assertServiceException(() -> agentSpecService.resolveCurrentVersion(specId),
                SESSION_ASSEMBLE_INVALID, "规格尚未发布版本");

        // 发布两版后切回 v1：解析到的生效快照随指针走
        agentSpecService.publishSpec(publishCommand(specId, null));
        agentSpecController.updateSpec(updateCommand(specId, "提示"));
        agentSpecService.publishSpec(publishCommand(specId, null));
        AgentSpecSwitchVersionCommand backToV1 = new AgentSpecSwitchVersionCommand();
        backToV1.setId(specId);
        backToV1.setVersionNo(1);
        agentSpecService.switchSpecVersion(backToV1);

        var byId = agentSpecService.resolveCurrentVersion(specId);
        assertEquals("cs-resolve", byId.spec().getSpecCode());
        assertEquals(1, byId.version().getVersionNo(), "生效快照 = 当前版本指针指向的版本");

        // 按业务编码寻址（OpenAI 出口 model 路由）同口径
        var byCode = agentSpecService.resolveCurrentVersionByCode("cs-resolve");
        assertEquals(specId, byCode.spec().getId());
        assertEquals(1, byCode.version().getVersionNo());

        // 寻址失败：规格不存在
        assertServiceException(() -> agentSpecService.resolveCurrentVersion(9999L),
                AGENT_SPEC_NOT_EXISTS);
        assertServiceException(() -> agentSpecService.resolveCurrentVersionByCode("no-such-code"),
                AGENT_SPEC_NOT_EXISTS);
    }

}
