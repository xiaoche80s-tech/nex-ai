package com.gkht.ai.nexai.module.ai.support;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecVersionMapper;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.dataobject.TenantApiKeyDO;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.mapper.TenantApiKeyMapper;
import com.gkht.ai.nexai.module.ai.audit.infrastructure.dataobject.AuditEventDO;
import com.gkht.ai.nexai.module.ai.audit.infrastructure.mapper.AuditEventMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ChannelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ModelDO;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ChannelMapper;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper.ModelMapper;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.dataobject.McpServerDO;
import com.gkht.ai.nexai.module.ai.mcpserver.infrastructure.mapper.McpServerMapper;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.dataobject.SkillVersionDO;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import com.gkht.ai.nexai.module.ai.usage.infrastructure.dataobject.ModelUsageDO;
import com.gkht.ai.nexai.module.ai.usage.infrastructure.mapper.ModelUsageMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 多租户隔离全聚合抽查（工单 17 MVP 集成验收）：验证所有 AI 聚合表经
 * {@link com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor}
 * 的租户过滤生效——租户 B 的查询上下文看不到租户 A 的数据。
 *
 * <p>覆盖 spec User Story 28 的七个维度：规格/渠道/技能/MCP/会话/审计+用量/API Key。
 * 每张表走 Mapper 直插 + Mapper selectList 验证（拦截器在 MyBatis 层生效，不需走 Controller）。</p>
 */
@Import({TenantDbTestConfiguration.class,
        AgentSpecMapper.class, AgentSpecVersionMapper.class,
        ChannelMapper.class, ModelMapper.class,
        SkillMapper.class, SkillVersionMapper.class,
        McpServerMapper.class, SessionMapper.class,
        AuditEventMapper.class, ModelUsageMapper.class,
        TenantApiKeyMapper.class})
@DisplayName("多租户隔离全聚合抽查（工单 17）")
public class MultiTenantIsolationTest extends BaseDbUnitTest {

    private static final long TENANT_A = 100L;
    private static final long TENANT_B = 200L;

    @Resource private AgentSpecMapper agentSpecMapper;
    @Resource private AgentSpecVersionMapper agentSpecVersionMapper;
    @Resource private ChannelMapper channelMapper;
    @Resource private ModelMapper modelMapper;
    @Resource private SkillMapper skillMapper;
    @Resource private SkillVersionMapper skillVersionMapper;
    @Resource private McpServerMapper mcpServerMapper;
    @Resource private SessionMapper sessionMapper;
    @Resource private AuditEventMapper auditEventMapper;
    @Resource private ModelUsageMapper modelUsageMapper;
    @Resource private TenantApiKeyMapper tenantApiKeyMapper;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ---------- 规格 ----------

    @Test
    @DisplayName("AgentSpec 租户隔离：租户 B 看不到租户 A 的规格")
    void agentSpecIsolation() {
        AgentSpecDO spec = new AgentSpecDO();
        spec.setName("隔离测试规格");
        spec.setSpecCode("iso-spec");
        spec.setOwnerLevel("TENANT");
        agentSpecMapper.insert(spec);
        assertNotNull(spec.getId());

        assertEquals(1, agentSpecMapper.selectList().size(), "租户 A 应看到自己的规格");

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, agentSpecMapper.selectList().size(), "租户 B 不应看到租户 A 的规格");
    }

    @Test
    @DisplayName("AgentSpecVersion 租户隔离：租户 B 看不到租户 A 的版本快照")
    void agentSpecVersionIsolation() {
        AgentSpecVersionDO version = new AgentSpecVersionDO();
        version.setSpecId(1L);
        version.setVersionNo(1);
        version.setConfig("{\"agent\":{}}");
        agentSpecVersionMapper.insert(version);

        assertEquals(1, agentSpecVersionMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, agentSpecVersionMapper.selectList().size());
    }

    // ---------- 渠道 / 模型 ----------

    @Test
    @DisplayName("Channel 租户隔离：租户 B 看不到租户 A 的渠道")
    void channelIsolation() {
        ChannelDO channel = new ChannelDO();
        channel.setName("隔离测试渠道");
        channel.setProvider("openai-compat");
        channel.setBaseUrl("http://localhost:18080");
        channel.setApiKey("test-key");
        channel.setEnabled(true);
        channel.setOwnerType("tenant");
        channelMapper.insert(channel);

        assertEquals(1, channelMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, channelMapper.selectList().size());
    }

    @Test
    @DisplayName("Model 租户隔离：租户 B 看不到租户 A 的模型")
    void modelIsolation() {
        ModelDO model = new ModelDO();
        model.setChannelId(1L);
        model.setModelId("test-model");
        model.setName("隔离测试模型");
        model.setEnabled(true);
        modelMapper.insert(model);

        assertEquals(1, modelMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, modelMapper.selectList().size());
    }

    // ---------- Skill ----------

    @Test
    @DisplayName("Skill 租户隔离：租户 B 看不到租户 A 的技能")
    void skillIsolation() {
        SkillDO skill = new SkillDO();
        skill.setName("隔离测试技能");
        skill.setDescription("测试用");
        skill.setOwnerLevel("TENANT");
        skillMapper.insert(skill);

        assertEquals(1, skillMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, skillMapper.selectList().size());
    }

    @Test
    @DisplayName("SkillVersion 租户隔离：租户 B 看不到租户 A 的技能版本")
    void skillVersionIsolation() {
        SkillVersionDO version = new SkillVersionDO();
        version.setSkillId(1L);
        version.setVersionNo(1);
        version.setSkillMarkdown("# 隔离测试技能内容");
        skillVersionMapper.insert(version);

        assertEquals(1, skillVersionMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, skillVersionMapper.selectList().size());
    }

    // ---------- MCP Server ----------

    @Test
    @DisplayName("McpServer 租户隔离：租户 B 看不到租户 A 的 MCP Server")
    void mcpServerIsolation() {
        McpServerDO server = new McpServerDO();
        server.setName("隔离测试 MCP");
        server.setTransport("sse");
        server.setEndpoint("http://localhost:3000");
        server.setEnabled(true);
        server.setOwnerType("tenant");
        mcpServerMapper.insert(server);

        assertEquals(1, mcpServerMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, mcpServerMapper.selectList().size());
    }

    // ---------- Session ----------

    @Test
    @DisplayName("Session 租户隔离：租户 B 看不到租户 A 的会话")
    void sessionIsolation() {
        SessionDO session = new SessionDO();
        session.setSessionKey("iso-session-key");
        session.setTitle("隔离测试会话");
        session.setType("DEBUG");
        session.setSpecId(1L);
        session.setStatus("READY");
        session.setRounds("[]");
        sessionMapper.insert(session);

        assertEquals(1, sessionMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, sessionMapper.selectList().size());
    }

    // ---------- 审计 / 用量 ----------

    @Test
    @DisplayName("AuditEvent 租户隔离：租户 B 看不到租户 A 的审计事件")
    void auditEventIsolation() {
        AuditEventDO event = new AuditEventDO();
        event.setSessionKey("iso-audit-key");
        event.setToolName("echo");
        event.setOutcome("SUCCESS");
        event.setOccurredAt(LocalDateTime.now());
        auditEventMapper.insert(event);

        assertEquals(1, auditEventMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, auditEventMapper.selectList().size());
    }

    @Test
    @DisplayName("ModelUsage 租户隔离：租户 B 看不到租户 A 的模型用量")
    void modelUsageIdolation() {
        ModelUsageDO usage = new ModelUsageDO();
        usage.setSessionKey("iso-usage-key");
        usage.setModelName("test-model");
        usage.setInputTokens(10);
        usage.setOutputTokens(5);
        usage.setTotalTokens(15);
        usage.setOccurredAt(LocalDateTime.now());
        modelUsageMapper.insert(usage);

        assertEquals(1, modelUsageMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, modelUsageMapper.selectList().size());
    }

    // ---------- API Key ----------

    @Test
    @DisplayName("TenantApiKey 租户隔离：租户 B 看不到租户 A 的 API Key")
    void apiKeyIsolation() {
        TenantApiKeyDO key = new TenantApiKeyDO();
        key.setName("隔离测试 Key");
        key.setKeyPrefix("nx_test");
        key.setKeyHash("abc123hash");
        key.setStatus("ENABLED");
        tenantApiKeyMapper.insert(key);

        assertEquals(1, tenantApiKeyMapper.selectList().size());

        TenantContextHolder.setTenantId(TENANT_B);
        assertEquals(0, tenantApiKeyMapper.selectList().size());
    }
}
