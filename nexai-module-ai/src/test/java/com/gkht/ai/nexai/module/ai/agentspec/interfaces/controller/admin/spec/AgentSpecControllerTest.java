package com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecService;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecVersionMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository.AgentSpecRepositoryImpl;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ModelDTO;
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
import com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model.ChannelController;
import com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model.ModelController;
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
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_CODE_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_EDIT_FORBIDDEN;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_PUBLISH_WITHOUT_DRAFT;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智能体规格 S1 接缝测试：直接调用 /admin-api/ai/spec/** 对应的控制器方法，
 * 走真实 controller → application → repository → H2 全链路，只断言外部行为。
 *
 * <p>模型引用经真实 model 聚合链路创建；租户拦截按 ModelControllerTest 惯例装配。</p>
 */
@Import({AgentSpecController.class, AgentSpecServiceImpl.class, AgentSpecRepositoryImpl.class,
        AgentSpecConverterImpl.class,
        ModelController.class, ModelServiceImpl.class, ModelRepositoryImpl.class, ModelConverterImpl.class,
        ChannelController.class, ChannelServiceImpl.class, ChannelRepositoryImpl.class, ChannelConverterImpl.class,
        AgentSpecControllerTest.TenantDbTestConfiguration.class,
        AgentSpecControllerTest.FakeGatewayConfiguration.class})
public class AgentSpecControllerTest extends BaseDbAndRedisUnitTest {

    @Resource
    private AgentSpecController agentSpecController;

    @Resource
    private AgentSpecService agentSpecService;

    @Resource
    private ModelController modelController;

    @Resource
    private ChannelController channelController;

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private AgentSpecVersionMapper agentSpecVersionMapper;

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

    /**
     * 连通性网关桩：本测试不触发连通性测试，仅满足 ModelServiceImpl 装配依赖（不外呼真实网关）
     */
    @TestConfiguration
    static class FakeGatewayConfiguration {

        @Bean
        public ModelConnectivityGateway modelConnectivityGateway() {
            return (channel, modelId) -> ConnectivityResult.success(0L, "stub");
        }

    }

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    /** 建一个 openai 渠道并在其下登记模型，返回模型编号（规格草稿的引用目标） */
    private Long createModel(String modelName) {
        ChannelCreateCommand channelCommand = new ChannelCreateCommand();
        channelCommand.setName("OpenAI 主渠道");
        channelCommand.setProvider(ChannelProvider.OPENAI.getCode());
        channelCommand.setBaseUrl("https://api.openai.com/v1");
        Long channelId = channelController.createChannel(channelCommand).getData();

        ModelCreateCommand modelCommand = new ModelCreateCommand();
        modelCommand.setChannelId(channelId);
        modelCommand.setModelId("gpt-4o");
        modelCommand.setName(modelName);
        modelCommand.setContextWindow(128_000);
        modelCommand.setInputPrice(new BigDecimal("0.5"));
        modelCommand.setOutputPrice(new BigDecimal("2.5"));
        return modelController.createModel(modelCommand).getData();
    }

    private AgentSpecCreateCommand createCommand(Long modelId) {
        AgentSpecCreateCommand command = new AgentSpecCreateCommand();
        command.setName("客服助手");
        command.setSpecCode("customer-service");
        command.setDescription("回答客户咨询");
        command.setIcon("ep:service");
        command.setModelId(modelId);
        command.setSystemPrompt("你是企业的智能客服");
        command.setMaxIters(10);
        command.setTemperature(0.7d);
        return command;
    }

    private AgentSpecUpdateCommand updateCommand(Long id, Long modelId) {
        AgentSpecUpdateCommand command = new AgentSpecUpdateCommand();
        command.setId(id);
        command.setName("客服助手");
        command.setDescription("回答客户咨询");
        command.setIcon("ep:service");
        command.setModelId(modelId);
        command.setSystemPrompt("你是企业的资深客服");
        command.setMaxIters(15);
        command.setTemperature(0.3d);
        return command;
    }

    @Test
    @DisplayName("创建规格：草稿 JSON 落库、未发布（无版本记录）、租户自动归属")
    public void createSpecPersistsDraft() throws SQLException {
        Long modelId = createModel("GPT-4o 主力");

        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();

        assertNotNull(specId);
        // 参数化查询断言草稿 JSON 与指针字段的落库形态
        String draftSql = "SELECT draft, latest_version_no, current_version_no, tenant_id FROM ai_agent_spec WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(draftSql)) {
            statement.setLong(1, specId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                String draft = resultSet.getString(1);
                assertTrue(draft.contains("你是企业的智能客服"));
                assertTrue(draft.contains(String.valueOf(modelId)));
                assertEquals(0, resultSet.getInt(2));
                assertNull(resultSet.getObject(3));
                assertEquals(1L, resultSet.getLong(4));
            }
        }
        assertEquals(0, agentSpecVersionMapper.selectCount());
    }

    @Test
    @DisplayName("创建规格：引用不存在的模型报 MODEL_NOT_EXISTS")
    public void createSpecModelNotExists() {
        assertServiceException(() -> agentSpecController.createSpec(createCommand(999L)), MODEL_NOT_EXISTS);
    }

    @Test
    @DisplayName("编辑草稿：覆盖草稿内容；发布后再编辑即生成新草稿")
    public void updateSpecOverwritesDraft() {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();

        agentSpecController.updateSpec(updateCommand(specId, modelId));

        AgentSpecDetailDTO detail = agentSpecController.getSpec(specId).getData();
        assertEquals("你是企业的资深客服", detail.getDraft().getSystemPrompt());
        assertEquals(15, detail.getDraft().getMaxIters());
        assertEquals(0.3d, detail.getDraft().getGenerateOptions().getTemperature());
        // 发布后再编辑 → 新草稿
        publish(specId, "v1");
        agentSpecController.updateSpec(updateCommand(specId, modelId));
        assertTrue(agentSpecController.getSpec(specId).getData().getHasDraft());
    }

    @Test
    @DisplayName("发布：草稿固化为 v1 快照、默认指针前移、草稿清空；无草稿再发布报错")
    public void publishLocksImmutableSnapshot() throws SQLException {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();

        assertEquals(1, agentSpecController.publishSpec(publishCommand(specId, "首个版本")).getData());

        // 版本表落库快照；规格表指针前移、草稿清空（参数化查询断言）
        String pointerSql = "SELECT s.current_version_no, s.draft, v.version_no, v.snapshot, v.remark FROM ai_agent_spec s JOIN ai_agent_spec_version v ON v.spec_id = s.id WHERE s.id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(pointerSql)) {
            statement.setLong(1, specId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                assertEquals(1, resultSet.getInt(1));
                assertNull(resultSet.getString(2));
                assertEquals(1, resultSet.getInt(3));
                String snapshot = resultSet.getString(4);
                assertTrue(snapshot.contains("你是企业的智能客服"));
                assertTrue(snapshot.contains("\"maxIters\":10"));
                assertEquals("首个版本", resultSet.getString(5));
            }
        }
        // 无草稿再发布被拒绝
        assertServiceException(() -> agentSpecController.publishSpec(publishCommand(specId, null)),
                AGENT_SPEC_PUBLISH_WITHOUT_DRAFT);
        // 发布后详情：hasDraft 必须是显式 false 而非 null（MapStruct 可空 source 条件包裹回归断言）
        assertFalse(agentSpecController.getSpec(specId).getData().getHasDraft());
    }

    @Test
    @DisplayName("版本不可变：v2 发布后 v1 快照保持原样（行为级断言）")
    public void publishedSnapshotNeverChanges() {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();
        publish(specId, "v1");

        agentSpecController.updateSpec(updateCommand(specId, modelId));
        assertEquals(2, agentSpecController.publishSpec(publishCommand(specId, "v2")).getData());

        List<AgentSpecVersionDTO> versions = agentSpecController.getVersionList(specId).getData();
        assertEquals(2, versions.size());
        // 倒序：v2 在前，v1 快照仍是发布时内容
        assertEquals(2, versions.get(0).getVersionNo());
        assertEquals("你是企业的资深客服", versions.get(0).getConfig().getSystemPrompt());
        assertEquals(1, versions.get(1).getVersionNo());
        assertEquals("你是企业的智能客服", versions.get(1).getConfig().getSystemPrompt());
        assertEquals(10, versions.get(1).getConfig().getMaxIters());
        assertEquals("v1", versions.get(1).getRemark());
    }

    @Test
    @DisplayName("发布：草稿引用的模型已被删除时报 MODEL_NOT_EXISTS，而非产出悬空快照")
    public void publishValidatesModelStillExists() {
        Long modelId = createModel("将被删除的模型");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();
        modelController.deleteModel(modelId);

        assertServiceException(() -> agentSpecController.publishSpec(publishCommand(specId, null)), MODEL_NOT_EXISTS);
        assertEquals(0, agentSpecVersionMapper.selectCount());
    }

    @Test
    @DisplayName("切换默认版本：可切回历史版本，越界版本号报 AGENT_SPEC_VERSION_NOT_EXISTS")
    public void switchDefaultVersion() {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();
        publish(specId, null);
        agentSpecController.updateSpec(updateCommand(specId, modelId));
        publish(specId, null);

        AgentSpecSwitchVersionCommand rollback = new AgentSpecSwitchVersionCommand();
        rollback.setId(specId);
        rollback.setVersionNo(1);
        assertEquals(SUCCESS.getCode(), agentSpecController.switchDefaultVersion(rollback).getCode());
        assertEquals(1, agentSpecController.getSpec(specId).getData().getCurrentVersionNo());
        // 详情的当前版本快照随之切换
        assertEquals("你是企业的智能客服",
                agentSpecController.getSpec(specId).getData().getCurrentVersion().getConfig().getSystemPrompt());

        AgentSpecSwitchVersionCommand invalid = new AgentSpecSwitchVersionCommand();
        invalid.setId(specId);
        invalid.setVersionNo(3);
        assertServiceException(() -> agentSpecController.switchDefaultVersion(invalid),
                AGENT_SPEC_VERSION_NOT_EXISTS);
    }

    @Test
    @DisplayName("分页查询：按名称过滤，行补充模型显示名（草稿优先，无草稿回退默认版本快照）")
    public void getSpecPageWithModelName() {
        Long modelId = createModel("GPT-4o 主力");
        agentSpecController.createSpec(createCommand(modelId)).getData();
        // 第二个规格：发布后无草稿，行模型名来自默认版本快照
        AgentSpecCreateCommand second = createCommand(modelId);
        second.setName("翻译助手");
        second.setSpecCode("translator");
        Long secondId = agentSpecController.createSpec(second).getData();
        publish(secondId, null);

        AgentSpecPageQuery query = new AgentSpecPageQuery();
        query.setName("客服");
        PageResult<AgentSpecDTO> page = agentSpecController.getSpecPage(query).getData();
        assertEquals(1, page.getTotal());
        assertEquals("GPT-4o 主力", page.getList().get(0).getModelName());
        assertTrue(page.getList().get(0).getHasDraft());

        AgentSpecPageQuery all = new AgentSpecPageQuery();
        PageResult<AgentSpecDTO> allPage = agentSpecController.getSpecPage(all).getData();
        assertEquals(2, allPage.getTotal());
        AgentSpecDTO publishedRow = allPage.getList().stream()
                .filter(row -> row.getId().equals(secondId)).findFirst().orElseThrow();
        assertEquals("GPT-4o 主力", publishedRow.getModelName());
        assertFalse(publishedRow.getHasDraft());
    }

    @Test
    @DisplayName("详情：未发布时当前版本为空、草稿完整；模型名一并补充")
    public void getSpecDetailBeforePublish() {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();

        AgentSpecDetailDTO detail = agentSpecController.getSpec(specId).getData();

        assertEquals("客服助手", detail.getName());
        assertTrue(detail.getHasDraft());
        assertNull(detail.getCurrentVersion());
        assertEquals("GPT-4o 主力", detail.getDraft().getModelName());
        assertEquals(0.7d, detail.getDraft().getGenerateOptions().getTemperature());
    }

    @Test
    @DisplayName("spec_code 唯一性：同归属层级重复被拒，不同层级/不同编码可用")
    public void specCodeUniquenessByOwnerLevel() {
        Long modelId = createModel("GPT-4o 主力");
        agentSpecController.createSpec(createCommand(modelId)).getData();

        // 同租户级同编码重复
        assertServiceException(() -> agentSpecController.createSpec(createCommand(modelId)),
                AGENT_SPEC_CODE_DUPLICATE, "customer-service");
        // 用户级与租户级同编码不冲突（层级隔离）
        AgentSpecCreateCommand userLevel = createCommand(modelId);
        userLevel.setOwnerLevel("USER");
        Long userSpecId = agentSpecService.createSpec(userLevel, 7L);
        assertNotNull(userSpecId);
        // 同一用户内重复仍拒
        AgentSpecCreateCommand userLevelDup = createCommand(modelId);
        userLevelDup.setOwnerLevel("USER");
        Long anotherSpecId = agentSpecService.createSpec(userLevelDup, 8L);
        assertNotNull(anotherSpecId);
        assertServiceException(() -> agentSpecService.createSpec(createCommand(modelId), 7L),
                AGENT_SPEC_CODE_DUPLICATE, "customer-service");
    }

    @Test
    @DisplayName("用户级可见性：分页仅归属用户可见租户级 + 自己的用户级规格")
    public void ownerLevelVisibility() {
        Long modelId = createModel("GPT-4o 主力");
        agentSpecController.createSpec(createCommand(modelId)).getData();
        AgentSpecCreateCommand mine = createCommand(modelId);
        mine.setSpecCode("my-private");
        mine.setOwnerLevel("USER");
        agentSpecService.createSpec(mine, 7L).longValue();
        AgentSpecCreateCommand others = createCommand(modelId);
        others.setSpecCode("others-private");
        others.setOwnerLevel("USER");
        agentSpecService.createSpec(others, 8L);

        // 归属用户 7：租户级 1 条 + 自己 1 条
        assertEquals(2, agentSpecService.getSpecPage(new AgentSpecPageQuery(), 7L).getTotal());
        // 用户 8：租户级 1 条 + 自己 1 条（看不到 7 的私享规格）
        PageResult<AgentSpecDTO> page8 = agentSpecService.getSpecPage(new AgentSpecPageQuery(), 8L);
        assertEquals(2, page8.getTotal());
        assertTrue(page8.getList().stream().noneMatch(row -> "my-private".equals(row.getSpecCode())));
        // 无登录态：仅非用户级
        assertEquals(1, agentSpecService.getSpecPage(new AgentSpecPageQuery(), null).getTotal());
    }

    @Test
    @DisplayName("用户级编辑权：非归属用户更新/发布/删除被拒，归属用户可用")
    public void userLevelEditPermission() {
        Long modelId = createModel("GPT-4o 主力");
        AgentSpecCreateCommand mine = createCommand(modelId);
        mine.setOwnerLevel("USER");
        Long specId = agentSpecService.createSpec(mine, 7L);

        AgentSpecUpdateCommand update = updateCommand(specId, modelId);
        assertServiceException(() -> agentSpecService.updateSpec(update, 8L), AGENT_SPEC_EDIT_FORBIDDEN);
        assertServiceException(() -> agentSpecService.deleteSpec(specId, 8L), AGENT_SPEC_EDIT_FORBIDDEN);
        assertServiceException(() -> agentSpecService.publishSpec(publishCommand(specId, null), 8L),
                AGENT_SPEC_EDIT_FORBIDDEN);

        agentSpecService.updateSpec(update, 7L);
        assertEquals(1, agentSpecService.publishSpec(publishCommand(specId, null), 7L));
    }

    @Test
    @DisplayName("编辑不改变 spec_code 与归属：编辑后身份字段保持创建时值")
    public void specCodeImmutableAcrossEdit() {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();

        agentSpecController.updateSpec(updateCommand(specId, modelId));

        AgentSpecDetailDTO detail = agentSpecController.getSpec(specId).getData();
        assertEquals("customer-service", detail.getSpecCode());
        assertEquals("TENANT", detail.getOwnerLevel());
        assertNull(detail.getOwnerUserId());
    }

    @Test
    @DisplayName("创建规格：平台级归属 M1 被拒（服务层防线，命令层 @Pattern 之外）")
    public void platformOwnerRejectedInM1() {
        Long modelId = createModel("GPT-4o 主力");
        AgentSpecCreateCommand platform = createCommand(modelId);
        platform.setOwnerLevel("PLATFORM");
        assertServiceException(() -> agentSpecService.createSpec(platform, null),
                AGENT_SPEC_OWNER_LEVEL_UNSUPPORTED);
    }

    @Test
    @DisplayName("版本历史：查询与详情均校验规格存在")
    public void requireSpecExists() {
        assertServiceException(() -> agentSpecController.getSpec(999L), AGENT_SPEC_NOT_EXISTS);
        assertServiceException(() -> agentSpecController.getVersionList(999L), AGENT_SPEC_NOT_EXISTS);
        assertServiceException(() -> agentSpecController.publishSpec(publishCommand(999L, null)),
                AGENT_SPEC_NOT_EXISTS);
    }

    @Test
    @DisplayName("删除规格：规格与版本均逻辑删除")
    public void deleteSpecCascade() {
        Long modelId = createModel("GPT-4o 主力");
        Long specId = agentSpecController.createSpec(createCommand(modelId)).getData();
        publish(specId, null);

        assertEquals(SUCCESS.getCode(), agentSpecController.deleteSpec(specId).getCode());

        assertNull(agentSpecMapper.selectById(specId));
        assertEquals(0, agentSpecVersionMapper.selectCount());
        assertServiceException(() -> agentSpecController.getSpec(specId), AGENT_SPEC_NOT_EXISTS);
    }

    @Test
    @DisplayName("租户隔离：租户 1 的规格对租户 2 不可见")
    public void tenantIsolation() {
        Long modelId = createModel("GPT-4o 主力");
        agentSpecController.createSpec(createCommand(modelId));

        TenantContextHolder.setTenantId(2L);
        assertEquals(0, agentSpecController.getSpecPage(new AgentSpecPageQuery()).getData().getTotal());

        TenantContextHolder.setTenantId(1L);
        assertEquals(1, agentSpecController.getSpecPage(new AgentSpecPageQuery()).getData().getTotal());
    }

    @Test
    @DisplayName("启用模型精简列表：供规格编辑下拉（模型聚合补充接口）")
    public void getEnabledModelList() {
        Long modelId = createModel("GPT-4o 主力");

        List<ModelDTO> models = modelController.getEnabledModelList().getData();

        assertEquals(1, models.size());
        assertEquals(modelId, models.get(0).getId());
    }

    private AgentSpecPublishCommand publishCommand(Long specId, String remark) {
        AgentSpecPublishCommand command = new AgentSpecPublishCommand();
        command.setId(specId);
        command.setRemark(remark);
        return command;
    }

    private void publish(Long specId, String remark) {
        agentSpecController.publishSpec(publishCommand(specId, remark));
    }

}
