package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.GenerateOptions;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import com.gkht.ai.nexai.module.ai.support.BasePgDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.support.ConfirmableTool;
import com.gkht.ai.nexai.module.ai.support.FakeChatModel;
import com.gkht.ai.nexai.module.ai.framework.config.AiRuntimeProperties;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行时网关契约测试（PG 直连，接缝 = ChatModelFactory stub + FakeChatModel）：
 * 覆盖工单 05 的 Mock 模型事件流契约、PG 会话状态持久化（跨轮次上下文恢复）、
 * 以及「无 per-请求实例创建」——同一装配指令的多次调用复用常驻实例（由装配次数断言证明）。
 */
@Import({AgentscopeRuntimeGateway.class, AiRuntimeProperties.class,
        com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway.ChatModelFactory.class,
        AgentscopeRuntimeGatewayTest.HitlToolConfiguration.class})
public class AgentscopeRuntimeGatewayTest extends BasePgDbAndRedisUnitTest {

    /** HITL 测试工具装配：注册敏感工具（checkPermissions 恒 ASK），经 RuntimeToolContributor 注入装配链 */
    @TestConfiguration
    static class HitlToolConfiguration {

        @Bean
        public com.gkht.ai.nexai.module.ai.session.infrastructure.gateway.RuntimeToolContributor
        sensitiveToolContributor() {
            return toolkit -> toolkit.registerTool(new ConfirmableTool("sensitive_op"));
        }
    }

    @Resource
    private AgentRuntimeGateway runtimeGateway;

    /** 模型构造接缝：测试中 stub 为 FakeChatModel，装配链其余全真实 */
    @MockitoBean
    private ChatModelFactory chatModelFactory;

    private static final String USER_ID = "t1-user";
    private static final String SESSION_KEY = "dbg-contract-test";

    /** 每个测试独立会话键（PG 状态槽位隔离，避免测试间上下文累积污染） */
    private String freshSessionKey() {
        return SESSION_KEY + "-" + System.nanoTime();
    }

    @AfterEach
    public void tearDown() {
        runtimeGateway.closeAll();
    }

    /** HITL 测试专用会话键（每次调用独立，避免测试间 PG 持久化槽位 ASKING 状态泄漏） */
    private static String hitlSessionKey() {
        return "dbg-hitl-" + System.nanoTime();
    }

    private AgentRuntimeConfig config() {
        return AgentRuntimeConfig.of(USER_ID, freshSessionKey(), 1L, "contract-agent", "contract-agent-v1",
                1L, 1, OwnerLevel.TENANT, null,
                "你是测试智能体", 5, GenerateOptions.of(0.5, null, null),
                ExecutionEnvConfig.disabled(),
                List.of(),
                channel(), model());
    }

    /** 固定会话键的装配指令（跨轮/跨调用测试用，验证状态持久化与实例复用） */
    private AgentRuntimeConfig configWithKey(String sessionKey) {
        return AgentRuntimeConfig.of(USER_ID, sessionKey, 1L, "contract-agent", "contract-agent-v1",
                1L, 1, OwnerLevel.TENANT, null,
                "你是测试智能体", 5, GenerateOptions.of(0.5, null, null),
                ExecutionEnvConfig.disabled(),
                List.of(),
                channel(), model());
    }

    private Channel channel() {
        // reconstitute 携带 id=1（真实链路渠道 id 由 DB 生成，tenantId 推导依赖它）
        Channel created = Channel.create("test-channel", ChannelProvider.OPENAI_COMPAT,
                "https://api.test.local/v1", "sk-test", ChannelOwnerType.TENANT);
        return Channel.reconstitute(1L, created.getName(), created.getProvider(),
                created.getBaseUrl(), created.getApiKey(), created.isEnabled(),
                created.getOwnerType(), LocalDateTime.now(), null);
    }

    private Model model() {
        return Model.create(1L, "fake-model", "测试模型", 8192, null, null);
    }

    /** 装配次数计数（新增测试经 thenAnswer 自建计数器，此处保留供原测试语义参考） */
    private void stubModel(FakeChatModel fake) {
        Mockito.when(chatModelFactory.create(Mockito.any(), Mockito.anyString())).thenReturn(fake);
    }

    @Resource
    private com.gkht.ai.nexai.module.ai.framework.config.AiRuntimeProperties runtimeProperties;

    @Test
    public void testChatEventStreamContract() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("你好，我是测试智能体")
                .build();
        stubModel(fake);

        List<RuntimeEvent> events = runtimeGateway.chat(config(), "你好")
                .collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());
        // 事件流契约：以 AGENT_START 开头、AGENT_END 收尾（agentscope streamEvents 标准序列）
        assertEquals(RuntimeEventType.AGENT_START, events.get(0).type());
        assertEquals(RuntimeEventType.AGENT_END, events.get(events.size() - 1).type());
        // 文本增量事件出现（模型回复被流式吐出）
        assertTrue(events.stream().anyMatch(e -> e.type() == RuntimeEventType.TEXT_BLOCK_DELTA),
                "应包含 TEXT_BLOCK_DELTA 文本增量事件");
        // 事件载荷为 agentscope 事件 JSON（含 type 判别字段）
        assertTrue(events.get(0).payload().contains("AGENT_START"));
        // 无错误收尾
        assertTrue(events.stream().noneMatch(e -> e.type() == RuntimeEventType.SESSION_ERROR),
                "Mock 模型回复不应出现 SESSION_ERROR");
    }

    @Test
    public void testStatePersistenceAcrossTurns() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("第一轮回复")
                .reply("第二轮回复")
                .build();
        stubModel(fake);

        // 第一轮：同 (userId, sessionKey) 槽位
        String fixedKey = "dbg-persistence-" + System.nanoTime();
        runtimeGateway.chat(configWithKey(fixedKey), "第一轮问题").collectList().block();
        // 第二轮：同一装配指令 → 同一常驻实例，槽位状态续接（上下文含第一轮）
        runtimeGateway.chat(configWithKey(fixedKey), "第二轮问题").collectList().block();

        // 断言模型调用包含第一轮上下文（会话状态经 PG 持久化恢复）。
        // 注意：ReActAgent 每轮可能多次调用模型（状态加载/系统提示注入），故断言"存在
        // 某次调用携带第一轮用户消息"而非精确调用次数。
        List<List<io.agentscope.core.message.Msg>> received = fake.getReceivedMessages();
        assertTrue(received.size() >= 2, "两轮应产生至少两次模型调用");
        boolean secondRoundHasHistory = received.subList(1, received.size()).stream()
                .anyMatch(msgs -> msgs.stream()
                        .map(m -> m.getContentBlocks(io.agentscope.core.message.TextBlock.class))
                        .flatMap(List::stream)
                        .map(io.agentscope.core.message.TextBlock::getText)
                        .reduce("", String::concat)
                        .contains("第一轮问题"));
        assertTrue(secondRoundHasHistory,
                "第二轮模型调用上下文应包含第一轮用户消息（PG 状态持久化恢复）");
    }

    @Test
    public void testNoPerRequestInstanceCreation() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("回复一")
                .reply("回复二")
                .reply("回复三")
                .build();
        stubModel(fake);

        // 同一装配指令连续三次调用（同 sessionKey 验证常驻实例复用）
        String fixedKey = "dbg-reuse-" + System.nanoTime();
        runtimeGateway.chat(configWithKey(fixedKey), "q1").collectList().block();
        runtimeGateway.chat(configWithKey(fixedKey), "q2").collectList().block();
        runtimeGateway.chat(configWithKey(fixedKey), "q3").collectList().block();

        // 无 per-请求实例创建：模型工厂只在首次装配时调用一次（常驻实例复用）
        Mockito.verify(chatModelFactory, Mockito.times(1))
                .create(Mockito.any(), Mockito.anyString());
    }

    @Test
    public void testErrorEventOnAssembleFailure() {
        // 模型工厂抛异常（模拟装配期错误）→ 事件流以 SESSION_ERROR 收尾而非抛异常
        Mockito.when(chatModelFactory.create(Mockito.any(), Mockito.anyString()))
                .thenThrow(new IllegalStateException("模型构造失败"));

        List<RuntimeEvent> events = runtimeGateway.chat(config(), "你好")
                .collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals(RuntimeEventType.SESSION_ERROR, events.get(0).type());
        assertTrue(events.get(0).payload().contains("模型构造失败"));
    }

    // ------------------------------------------------------------------
    //  工单 08：版本戳失效与配置热更
    // ------------------------------------------------------------------

    @Test
    public void testChannelUpdateInvalidatesInstance() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("旧渠道回复")
                .reply("新渠道回复")
                .build();
        // 模型工厂调用次数 = 装配次数（每次装配 create 一次）
        AtomicInteger creates = new AtomicInteger();
        Mockito.when(chatModelFactory.create(Mockito.any(), Mockito.anyString()))
                .thenAnswer(inv -> {
                    creates.incrementAndGet();
                    return fake;
                });

        // 首次装配（渠道 updateTime=null 与后续不同）
        runtimeGateway.chat(config(), "q1").collectList().block();
        assertEquals(1, creates.get(), "首次调用应装配一次");

        // 渠道更新时间变化（模拟渠道配置热更）→ 版本戳变化 → 重建实例
        AgentRuntimeConfig updated = AgentRuntimeConfig.of(USER_ID, SESSION_KEY,
                1L,
                "contract-agent", "contract-agent-v1", 1L, 1, OwnerLevel.TENANT, null,
                "你是测试智能体", 5, GenerateOptions.of(0.5, null, null),
                ExecutionEnvConfig.disabled(), List.of(),
                channelWithTime(LocalDateTime.now()), model());
        runtimeGateway.chat(updated, "q2").collectList().block();
        assertEquals(2, creates.get(), "渠道 updateTime 变化应触发重建（新实例）");
    }

    @Test
    public void testModelUpdateInvalidatesInstance() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("旧模型回复")
                .reply("新模型回复")
                .build();
        AtomicInteger creates = new AtomicInteger();
        Mockito.when(chatModelFactory.create(Mockito.any(), Mockito.anyString()))
                .thenAnswer(inv -> {
                    creates.incrementAndGet();
                    return fake;
                });

        runtimeGateway.chat(config(), "q1").collectList().block();
        assertEquals(1, creates.get());

        // 模型更新时间变化 → 版本戳变化 → 重建实例
        AgentRuntimeConfig updated = AgentRuntimeConfig.of(USER_ID, SESSION_KEY,
                1L,
                "contract-agent", "contract-agent-v1", 1L, 1, OwnerLevel.TENANT, null,
                "你是测试智能体", 5, GenerateOptions.of(0.5, null, null),
                ExecutionEnvConfig.disabled(), List.of(),
                channel(), modelWithTime(LocalDateTime.now()));
        runtimeGateway.chat(updated, "q2").collectList().block();
        assertEquals(2, creates.get(), "模型 updateTime 变化应触发重建（新实例）");
    }

    @Test
    public void testVersionChangeInvalidatesInstance() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("v1 回复")
                .reply("v2 回复")
                .build();
        AtomicInteger creates = new AtomicInteger();
        Mockito.when(chatModelFactory.create(Mockito.any(), Mockito.anyString()))
                .thenAnswer(inv -> {
                    creates.incrementAndGet();
                    return fake;
                });

        // v1 会话
        runtimeGateway.chat(config(), "q1").collectList().block();
        assertEquals(1, creates.get());

        // 规格发布 v2（版本号变化 → specReference 变化）→ 不同缓存键 → 重建实例
        AgentRuntimeConfig v2 = AgentRuntimeConfig.of(USER_ID, SESSION_KEY,
                1L,
                "contract-agent", "contract-agent-v2", 1L, 2, OwnerLevel.TENANT, null,
                "你是测试智能体 v2", 5, GenerateOptions.of(0.5, null, null),
                ExecutionEnvConfig.disabled(),
                List.of(),
                channel(), model());
        runtimeGateway.chat(v2, "q2").collectList().block();
        assertEquals(2, creates.get(), "版本号变化应触发重建（新实例）");
    }

    // ------------------------------------------------------------------
    //  工单 07：执行环境层（workspace 布局 + AGENTS.md 物化 + 文件栏）
    // ------------------------------------------------------------------

    @Test
    public void testWorkspaceLayoutAndAgentsMdMaterialization() throws Exception {
        FakeChatModel fake = FakeChatModel.script()
                .reply("workspace 智能体回复")
                .build();
        stubModel(fake);

        // workspace 开启的装配指令（系统提示 → AGENTS.md 物化源）
        AgentRuntimeConfig wsConfig = AgentRuntimeConfig.of(USER_ID, SESSION_KEY,
                1L,
                "ws-agent", "ws-agent-v1", 1L, 1, OwnerLevel.TENANT, null,
                "这是 workspace 智能体的系统提示", 5, null,
                ExecutionEnvConfig.of(true, false, null),
                List.of(),
                channel(), model());

        runtimeGateway.chat(wsConfig, "你好").collectList().block();

        // workspace 目录按归属层级创建：{root}/t{tenantId}/{specCode}（tenantId=1 由装配指令注入）
        Path workspaceRoot = runtimeProperties.getWorkspace().resolvedRoot();
        Path expected = workspaceRoot.resolve("t1").resolve("ws-agent");
        assertTrue(Files.isDirectory(expected), "workspace 目录应按归属层级创建：" + expected);

        // AGENTS.md 物化为系统提示（DB 快照唯一权威源 + 内容比对）
        Path agentsMd = expected.resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd), "AGENTS.md 应物化到 workspace 根");
        assertEquals("这是 workspace 智能体的系统提示", Files.readString(agentsMd));

        // 文件栏：列出 workspace 根（应含 AGENTS.md）
        List<String> files = runtimeGateway.listWorkspaceFiles(wsConfig, null);
        assertTrue(files.contains("AGENTS.md"), "文件栏应能列出 AGENTS.md，实际：" + files);

        // 文件读取：AGENTS.md 内容可读
        String content = runtimeGateway.readWorkspaceFile(wsConfig, "AGENTS.md");
        assertEquals("这是 workspace 智能体的系统提示", content);
    }

    @Test
    public void testWorkspaceFileReadRejectsPathEscape() {
        FakeChatModel fake = FakeChatModel.script()
                .reply("ok")
                .build();
        stubModel(fake);

        AgentRuntimeConfig wsConfig = AgentRuntimeConfig.of(USER_ID, SESSION_KEY,
                1L,
                "ws-agent", "ws-agent-v1", 1L, 1, OwnerLevel.TENANT, null,
                "系统提示", 5, null,
                ExecutionEnvConfig.of(true, false, null),
                List.of(),
                channel(), model());
        runtimeGateway.chat(wsConfig, "你好").collectList().block();

        // 路径越界（逃逸 workspace 根）→ 拒绝读取（返回 null）
        assertNull(runtimeGateway.readWorkspaceFile(wsConfig, "../secret.txt"));
    }

    // ------------------------------------------------------------------
    //  工单 09：HITL 三态（中断-确认-继续事件序列）
    // ------------------------------------------------------------------

    @Test
    public void testHitlInterruptConfirmResumeEventSequence() {
        // 脚本：第一步模型调用敏感工具 sensitive_op → 触发 ASKING 挂起；
        // 审批确认后恢复 → 工具执行 → ReAct 循环再次调模型确认结束（脚本耗尽自动空收尾）
        FakeChatModel fake = FakeChatModel.script()
                .callTool("sensitive_op", Map.of("action", "danger"))
                .reply("审批已通过，敏感操作完成")
                .build();
        stubModel(fake);

        // 带敏感工具挂载的装配指令：sensitiveTools 含 sensitive_op
        AgentRuntimeConfig hitlConfig = AgentRuntimeConfig.of(USER_ID, hitlSessionKey(),
                1L,
                "hitl-agent", "hitl-agent-v1", 1L, 1, OwnerLevel.TENANT, null,
                "你是测试智能体", 5, null,
                ExecutionEnvConfig.disabled(),
                List.of(com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount.of(
                        com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource.PLATFORM,
                        1L, List.of("sensitive_op"), List.of("sensitive_op"))),
                channel(), model());

        // 第一轮：敏感工具被调用 → 事件流应含 REQUIRE_USER_CONFIRM 挂起
        List<RuntimeEvent> first = runtimeGateway.chat(hitlConfig, "执行敏感操作").collectList().block();
        assertNotNull(first);
        boolean paused = first.stream().anyMatch(
                e -> e.type() == RuntimeEventType.REQUIRE_USER_CONFIRM);
        assertTrue(paused, "敏感工具调用应触发 REQUIRE_USER_CONFIRM 挂起，事件流："
                + first.stream().map(RuntimeEvent::type).toList());

        // 从挂起事件提取 toolCallId，构造审批决定（确认）
        RuntimeEvent confirmEvent = first.stream()
                .filter(e -> e.type() == RuntimeEventType.REQUIRE_USER_CONFIRM)
                .findFirst().orElseThrow();
        String toolCallId = extractToolCallId(confirmEvent.payload());

        // 第二轮：审批确认 → 续行 → 工具执行 + 模型回复
        List<RuntimeEvent> resumed = runtimeGateway.confirmToolCalls(hitlConfig,
                List.of(ToolCallDecision.of(toolCallId, "sensitive_op", true,
                        "{\"action\":\"danger\"}"))).collectList().block();
        assertNotNull(resumed);
        boolean toolResult = resumed.stream().anyMatch(
                e -> e.type() == RuntimeEventType.TOOL_RESULT_END
                        || e.type() == RuntimeEventType.TOOL_RESULT_TEXT_DELTA);
        assertTrue(toolResult, "审批确认后敏感工具应执行（TOOL_RESULT 事件），事件流："
                + resumed.stream().map(RuntimeEvent::type).toList());
        assertTrue(resumed.stream().anyMatch(e -> e.type() == RuntimeEventType.AGENT_END),
                "续行应以 AGENT_END 收尾");
        assertTrue(resumed.stream().noneMatch(e -> e.type() == RuntimeEventType.SESSION_ERROR),
                "审批确认路径不应出现 SESSION_ERROR");
    }

    @Test
    public void testHitlRejectResume() {
        FakeChatModel fake = FakeChatModel.script()
                .callTool("sensitive_op", Map.of("action", "danger"))
                .reply("已按拒绝处理")
                .build();
        stubModel(fake);

        AgentRuntimeConfig hitlConfig = AgentRuntimeConfig.of(USER_ID, hitlSessionKey(),
                1L,
                "hitl-agent", "hitl-agent-v1", 1L, 1, OwnerLevel.TENANT, null,
                "你是测试智能体", 5, null,
                ExecutionEnvConfig.disabled(),
                List.of(com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount.of(
                        com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource.PLATFORM,
                        1L, List.of("sensitive_op"), List.of("sensitive_op"))),
                channel(), model());

        List<RuntimeEvent> first = runtimeGateway.chat(hitlConfig, "执行敏感操作").collectList().block();
        RuntimeEvent confirmEvent = first.stream()
                .filter(e -> e.type() == RuntimeEventType.REQUIRE_USER_CONFIRM)
                .findFirst().orElseThrow();
        String toolCallId = extractToolCallId(confirmEvent.payload());

        // 拒绝 → 工具不执行（TOOL_RESULT DENIED），模型收到拒绝上下文继续回复
        List<RuntimeEvent> resumed = runtimeGateway.confirmToolCalls(hitlConfig,
                List.of(ToolCallDecision.of(toolCallId, "sensitive_op", false, null)))
                .collectList().block();
        assertNotNull(resumed);
        assertTrue(resumed.stream().anyMatch(e -> e.type() == RuntimeEventType.AGENT_END),
                "拒绝后应正常收尾");
    }

    /** 从 RequireUserConfirmEvent JSON 提取首个 toolCall.id */
    private String extractToolCallId(String payload) {
        var node = com.gkht.ai.nexai.framework.common.util.json.JsonUtils.parseTree(payload);
        return node.path("toolCalls").path(0).path("id").asText();
    }

    /** 渠道（带 updateTime） */
    private Channel channelWithTime(LocalDateTime updateTime) {
        Channel channel = channel();
        return Channel.reconstitute(channel.getId(), channel.getName(), channel.getProvider(),
                channel.getBaseUrl(), channel.getApiKey(), channel.isEnabled(),
                channel.getOwnerType(), LocalDateTime.now(), updateTime);
    }

    /** 模型（带 updateTime） */
    private Model modelWithTime(LocalDateTime updateTime) {
        Model model = Model.create(1L, "fake-model", "测试模型", 8192, null, null);
        return Model.reconstitute(1L, 1L, model.getModelId(), model.getName(),
                model.getContextWindow(), model.getInputPrice(), model.getOutputPrice(),
                model.isEnabled(), LocalDateTime.now(), updateTime);
    }

}
