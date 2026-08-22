package com.gkht.ai.nexai.module.ai.session.interfaces.controller.admin.session;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.type.EncryptTypeHandler;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.security.core.LoginUser;
import com.gkht.ai.nexai.framework.security.core.util.SecurityFrameworkUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.repository.AgentSpecRepositoryImpl;
import com.gkht.ai.nexai.module.ai.agentspec.interfaces.controller.admin.spec.AgentSpecController;
import com.gkht.ai.nexai.module.ai.model.application.command.ChannelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.service.ChannelServiceImpl;
import com.gkht.ai.nexai.module.ai.model.application.service.ModelServiceImpl;
import com.gkht.ai.nexai.module.ai.model.domain.gateway.ModelConnectivityGateway;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ConnectivityResult;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ChannelConverterImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.converter.ModelConverterImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.model.infrastructure.repository.ChannelRepositoryImpl;
import com.gkht.ai.nexai.module.ai.model.infrastructure.repository.ModelRepositoryImpl;
import com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model.ChannelController;
import com.gkht.ai.nexai.module.ai.model.interfaces.controller.admin.model.ModelController;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCloneCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionConfirmCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand;
import com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery;
import com.gkht.ai.nexai.module.ai.session.application.service.SessionServiceImpl;
import com.gkht.ai.nexai.module.ai.session.infrastructure.converter.SessionConverterImpl;
import com.gkht.ai.nexai.module.ai.session.infrastructure.gateway.AgentStateStoreProvider;
import com.gkht.ai.nexai.module.ai.session.infrastructure.gateway.RuntimeToolContributor;
import com.gkht.ai.nexai.module.ai.session.infrastructure.repository.SessionRepositoryImpl;
import com.gkht.ai.nexai.module.ai.support.BasePgDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.support.ConfirmableEchoTool;
import com.gkht.ai.nexai.module.ai.support.EchoTool;
import com.gkht.ai.nexai.module.ai.support.FakeChatModel;
import io.agentscope.core.tool.Toolkit;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_RUNNING;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_SPEC_NOT_PUBLISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调试会话 S1 接缝测试：直接调用 /admin-api/ai/session/** 对应的控制器方法，
 * 走真实 controller → application → repository → PostgreSQL 全链路，
 * 并以真实 agentscope 装配链 + PG 状态存储 + FakeChatModel（S2）
 * 断言事件流序列与最终结果——不 mock 运行时，只替换模型端点。
 *
 * <p>数据落专用租户 999999（与 local 手动验收数据隔离），每测试后经 clean.sql 精确清理；
 * agentscope 状态存储中的会话状态在 teardown 逐一删除。</p>
 */
@Import({DebugSessionController.class, SessionServiceImpl.class, SessionRepositoryImpl.class,
        SessionConverterImpl.class,
        com.gkht.ai.nexai.module.ai.session.infrastructure.gateway.AgentscopeRuntimeGateway.class,
        AgentStateStoreProvider.class,
        com.gkht.ai.nexai.module.ai.session.framework.config.AiRuntimeProperties.class,
        com.gkht.ai.nexai.module.ai.session.infrastructure.gateway.SandboxImageResolver.class,
        com.gkht.ai.nexai.module.ai.session.infrastructure.gateway.DockerAvailabilityProbe.class,
        AgentSpecController.class, AgentSpecServiceImpl.class, AgentSpecRepositoryImpl.class, AgentSpecConverterImpl.class,
        ModelController.class, ModelServiceImpl.class, ModelRepositoryImpl.class, ModelConverterImpl.class,
        ChannelController.class, ChannelServiceImpl.class, ChannelRepositoryImpl.class, ChannelConverterImpl.class,
        DebugSessionControllerTest.TenantDbTestConfiguration.class,
        DebugSessionControllerTest.FakeModelConfiguration.class,
        DebugSessionControllerTest.StubGatewayConfiguration.class,
        DebugSessionControllerTest.ToolContributorConfiguration.class})
public class DebugSessionControllerTest extends BasePgDbAndRedisUnitTest {

    /** 测试专用租户：与 local 环境手动数据（租户 1）隔离，清理脚本按此精确删除 */
    static final Long TEST_TENANT_ID = 999999L;

    /** 当前用例注入的 FakeChatModel（每个测试方法开头重设脚本） */
    static final AtomicReference<FakeChatModel> CURRENT_FAKE = new AtomicReference<>();

    /** 规格业务编码序号（spec_code 唯一性要求同用例多规格不撞码） */
    static final java.util.concurrent.atomic.AtomicInteger SPEC_CODE_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    private static final String SYSTEM_PROMPT = "你是测试智能体，请简洁作答";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(30);

    @Resource
    private DebugSessionController sessionController;

    @Resource
    private AgentSpecController agentSpecController;

    @Resource
    private ModelController modelController;

    @Resource
    private ChannelController channelController;

    @Resource
    private AgentStateStoreProvider agentStateStoreProvider;

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
     * 模型端点替换：ChatModelFactory 返回当前用例编排的 FakeChatModel（S2 接缝），
     * 装配链其余部分（ModelRegistry 注册、agent 构建、状态存储、事件流）全部真实执行
     */
    @TestConfiguration
    static class FakeModelConfiguration {

        @Bean
        public ChatModelFactory chatModelFactory() {
            return new ChatModelFactory() {
                @Override
                public io.agentscope.core.model.Model create(Channel channel, String modelId) {
                    return CURRENT_FAKE.get();
                }
            };
        }

    }

    /** 连通性网关桩：本测试不触发连通性测试，仅满足 ModelServiceImpl 装配依赖 */
    @TestConfiguration
    static class StubGatewayConfiguration {

        @Bean
        public ModelConnectivityGateway modelConnectivityGateway() {
            return (channel, modelId) -> ConnectivityResult.success(0L, "stub");
        }

    }

    /** 调试工具注入：验证工具调用事件流（M2 的技能/MCP 挂载走同一扩展点）；confirmable 工具驱动 HITL 三态 */
    @TestConfiguration
    static class ToolContributorConfiguration {

        @Bean
        public RuntimeToolContributor echoToolContributor() {
            return toolkit -> {
                toolkit.registerTool(new EchoTool());
                toolkit.registerAgentTool(new ConfirmableEchoTool());
            };
        }

    }

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TEST_TENANT_ID);
        CURRENT_FAKE.set(FakeChatModel.script().reply("默认应答").build());
        LoginUser loginUser = new LoginUser();
        loginUser.setId(1L);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(loginUser, null, "mock"));
        // 注入真实 AES（16 字节密钥），使渠道密钥列真实加解密（对齐 ChannelControllerTest 惯例）
        ReflectUtil.setFieldValue(EncryptTypeHandler.class, "aes", SecureUtil.aes("0123456789abcdef".getBytes()));
    }

    @AfterEach
    public void tearDown() {
        // 先清理 agentscope 状态存储中的会话状态（无租户概念，按运行时 userId 槽位逐一删除——
        // 槽位与发消息时一致：有登录态取登录用户编号，否则匿名），再释放租户上下文——其后由 clean.sql 清理业务表
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        String slotUserId = loginUserId != null ? String.valueOf(loginUserId)
                : SessionServiceImpl.ANONYMOUS_USER_ID;
        for (SessionDTO session : sessionController.getSessionPage(pageOf(null)).getData().getList()) {
            agentStateStoreProvider.get().delete(slotUserId, session.getSessionKey());
        }
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        // 复位静态 AES，避免测试密钥泄漏到同 JVM 的其他测试类
        ReflectUtil.setFieldValue(EncryptTypeHandler.class, "aes", null);
    }

    @Test
    @DisplayName("发消息：返回完整事件流序列（AGENT_START → 模型调用/文本块 → AGENT_RESULT → AGENT_END）")
    void sendMessage_streamsFullEventSequence() {
        CURRENT_FAKE.set(FakeChatModel.script().reply("你好，我是测试智能体").build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        List<String> events = collectEvents(sessionId, "打个招呼");

        assertEquals("AGENT_START", eventType(events.get(0)));
        assertEquals("AGENT_END", eventType(events.get(events.size() - 1)));
        List<String> types = events.stream().map(this::eventType).toList();
        assertTrue(types.containsAll(List.of("MODEL_CALL_START", "TEXT_BLOCK_START", "TEXT_BLOCK_DELTA",
                "TEXT_BLOCK_END", "MODEL_CALL_END", "AGENT_RESULT")),
                "事件流应覆盖模型调用与文本块全生命周期，实际序列：" + types);
        assertTrue(types.indexOf("TEXT_BLOCK_START") < types.indexOf("MODEL_CALL_END"),
                "文本块事件应发生在模型调用结束之前");
        assertTrue(events.stream().filter(event -> eventType(event).equals("TEXT_BLOCK_DELTA"))
                .anyMatch(event -> event.contains("你好，我是测试智能体")), "文本增量应携带回复内容");
        assertTrue(lastOf(events, "AGENT_RESULT").contains("你好，我是测试智能体"), "最终结果应携带回复内容");
    }

    @Test
    @DisplayName("发消息：系统提示随装配注入模型调用")
    void sendMessage_injectsSystemPrompt() {
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        collectEvents(sessionId, "第一问");

        FakeChatModel fake = CURRENT_FAKE.get();
        List<io.agentscope.core.message.Msg> firstRound = fake.getReceivedMessages().get(0);
        assertTrue(firstRound.stream().map(io.agentscope.core.message.Msg::getTextContent)
                        .filter(Objects::nonNull).anyMatch(text -> text.contains(SYSTEM_PROMPT)),
                "第一轮推理请求应携带规格快照中的系统提示");
    }

    @Test
    @DisplayName("多轮消息：第二轮模型请求含第一轮问答（PG 状态存储跨轮恢复上下文）")
    void sendMessage_restoresContextAcrossRounds() {
        CURRENT_FAKE.set(FakeChatModel.script()
                .reply("第一轮答复")
                .reply("第二轮答复")
                .build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        collectEvents(sessionId, "第一轮问题");
        collectEvents(sessionId, "第二轮问题");

        FakeChatModel fake = CURRENT_FAKE.get();
        assertEquals(2, fake.getReceivedMessages().size());
        String secondRoundContext = fake.getReceivedMessages().get(1).stream()
                .map(io.agentscope.core.message.Msg::getTextContent)
                .filter(Objects::nonNull).collect(Collectors.joining("\n"));
        assertTrue(secondRoundContext.contains("第一轮问题"), "第二轮请求应含第一轮用户消息");
        assertTrue(secondRoundContext.contains("第一轮答复"), "第二轮请求应含第一轮助手答复");
        // 轮次记录随消息递增
        assertEquals(2, sessionController.getSessionPage(pageOf(null)).getData().getList().get(0).getMessageRounds());
    }

    @Test
    @DisplayName("工具调用：事件流覆盖 TOOL_CALL 与 TOOL_RESULT，工具真实执行并回填")
    void sendMessage_executesToolCall() {
        CURRENT_FAKE.set(FakeChatModel.script()
                .callTool("echo", Map.of("text", "敲黑板"))
                .reply("工具执行完毕")
                .build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        List<String> events = collectEvents(sessionId, "请回显：敲黑板");
        List<String> types = events.stream().map(this::eventType).toList();

        assertTrue(types.contains("TOOL_CALL_START") && types.contains("TOOL_CALL_END"),
                "应有工具调用事件，实际序列：" + types);
        String toolResult = lastOf(events, "TOOL_RESULT_TEXT_DELTA");
        assertTrue(toolResult != null && toolResult.contains("echo:敲黑板"),
                "工具结果应来自真实执行的 EchoTool");
        // 第二轮推理收到的历史里带工具结果（工具结果存于 ToolResultBlock，经整体序列化断言）
        assertTrue(io.agentscope.core.util.JsonUtils.getJsonCodec()
                        .toJson(fake().getReceivedMessages().get(1)).contains("echo:敲黑板"),
                "下一轮推理请求应携带工具执行结果");
        assertTrue(lastOf(events, "AGENT_RESULT").contains("工具执行完毕"));
    }

    @Test
    @DisplayName("创建调试会话：缺省绑定规格当前默认版本，显式指定则绑定指定版本")
    void createDebugSession_bindsVersion() {
        Long specId = createPublishedSpec(); // 发布 v1（默认版本 1，草稿随之清空）

        // 再编辑生成新草稿后发布 v2（默认版本前移到 2）
        Long modelId = createChannelAndModel();
        AgentSpecUpdateCommand updateCommand = new AgentSpecUpdateCommand();
        updateCommand.setId(specId);
        updateCommand.setName("测试规格 v2");
        updateCommand.setDescription("调试台测试规格，验证事件流与装配链");
        updateCommand.setModelId(modelId);
        updateCommand.setSystemPrompt(SYSTEM_PROMPT);
        agentSpecController.updateSpec(updateCommand);
        agentSpecController.publishSpec(publishCommand(specId));

        Long defaultBound = createDebugSession(specId, null);
        Long explicitBound = createDebugSession(specId, 1);

        SessionPageQuery query = pageOf(null);
        PageResult<SessionDTO> page = sessionController.getSessionPage(query).getData();
        Map<Long, Integer> versionBySessionId = page.getList().stream()
                .collect(Collectors.toMap(SessionDTO::getId, SessionDTO::getVersionNo));
        assertEquals(2, versionBySessionId.get(defaultBound), "缺省绑定当前默认版本");
        assertEquals(1, versionBySessionId.get(explicitBound), "显式指定绑定 v1（会话绑定稳定版本）");
    }

    @Test
    @DisplayName("创建调试会话：规格从未发布时拒绝（无可绑定版本）")
    void createDebugSession_rejectsUnpublishedSpec() {
        Long modelId = createChannelAndModel();
        AgentSpecCreateCommand command = specCreateCommand(modelId);
        Long specId = agentSpecController.createSpec(command).getData(); // 只有草稿，从未发布

        DebugSessionCreateCommand createCommand = new DebugSessionCreateCommand();
        createCommand.setSpecId(specId);
        assertServiceException(() -> sessionController.createDebugSession(createCommand),
                SESSION_SPEC_NOT_PUBLISHED);
    }

    @Test
    @DisplayName("发消息：会话不存在时报业务错误")
    void sendMessage_rejectsUnknownSession() {
        DebugSessionMessageCommand command = new DebugSessionMessageCommand();
        command.setContent("你好");
        assertServiceException(() -> sessionController.sendMessage(999L, command), SESSION_NOT_EXISTS);
    }

    @Test
    @DisplayName("会话分页：按规格与版本过滤")
    void getSessionPage_filtersBySpecAndVersion() {
        Long specA = createPublishedSpec();
        Long specB = createPublishedSpec();
        createDebugSession(specA, null);
        createDebugSession(specB, null);

        PageResult<SessionDTO> page = sessionController.getSessionPage(pageOf(specA)).getData();
        assertEquals(1, page.getTotal());
        assertEquals(specA, page.getList().get(0).getSpecId());

        // 版本过滤：specA 再发布 v2 后，按 v2 过滤只出绑 v2 的会话
        Long modelId = createChannelAndModel();
        AgentSpecUpdateCommand updateCommand = new AgentSpecUpdateCommand();
        updateCommand.setId(specA);
        updateCommand.setName("测试规格 v2");
        updateCommand.setDescription("调试台测试规格，验证事件流与装配链");
        updateCommand.setModelId(modelId);
        updateCommand.setSystemPrompt(SYSTEM_PROMPT);
        agentSpecController.updateSpec(updateCommand);
        agentSpecController.publishSpec(publishCommand(specA));
        createDebugSession(specA, 2);

        SessionPageQuery byVersion = pageOf(specA);
        byVersion.setVersionNo(2);
        PageResult<SessionDTO> versionPage = sessionController.getSessionPage(byVersion).getData();
        assertEquals(1, versionPage.getTotal());
        assertEquals(2, versionPage.getList().get(0).getVersionNo());
    }

    // ==================== 工单 08：HITL 三态 / 中断 / 克隆重跑 ====================

    @Test
    @DisplayName("HITL：确认工具调用触发挂起（REQUIRE_USER_CONFIRM + REQUEST_STOP，工具不执行，流正常收尾）")
    void hitl_pausesStreamOnConfirmRequest() {
        CURRENT_FAKE.set(FakeChatModel.script()
                .callTool("confirmable_echo", Map.of("text", "敲黑板"))
                .reply("工具执行完毕")
                .build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        List<String> events = collectEvents(sessionId, "请回显：敲黑板");
        List<String> types = events.stream().map(this::eventType).toList();

        assertTrue(types.contains("REQUIRE_USER_CONFIRM"), "应发出确认请求事件，实际序列：" + types);
        assertTrue(types.contains("REQUEST_STOP"), "确认请求后应以 REQUEST_STOP 信号挂起本轮");
        assertEquals("AGENT_END", eventType(events.get(events.size() - 1)), "挂起轮事件流应正常收尾");
        // TOOL_CALL_* 生命周期事件表示调用被发起；闸门拦截的表现是永不出现工具执行结果
        assertFalse(types.stream().anyMatch(type -> type.startsWith("TOOL_RESULT")),
                "确认前工具不得真实执行，实际序列：" + types);
        String confirmEvent = lastOf(events, "REQUIRE_USER_CONFIRM");
        assertTrue(confirmEvent.contains("confirmable_echo") && confirmEvent.contains("fake-call-0"),
                "确认请求应携带待确认工具调用的名称与标识，实际：" + confirmEvent);
    }

    @Test
    @DisplayName("HITL：批准（原参数）→ 确认结果事件 + 工具真实执行 + 后续推理完成")
    void hitl_approveExecutesTool() {
        Long sessionId = preparedAskingSession();

        List<String> events = confirmAndCollect(sessionId, "fake-call-0", "confirmable_echo",
                "{\"text\":\"敲黑板\"}", true);
        List<String> types = events.stream().map(this::eventType).toList();

        assertTrue(types.contains("USER_CONFIRM_RESULT"), "回应后应发出确认结果事件，实际序列：" + types);
        String toolResult = lastOf(events, "TOOL_RESULT_TEXT_DELTA");
        assertTrue(toolResult != null && toolResult.contains("confirmed-echo:敲黑板"),
                "批准后工具应以原参数真实执行，实际：" + toolResult);
        assertTrue(lastOf(events, "AGENT_RESULT").contains("工具执行完毕"), "工具结果应驱动后续推理完成");
        assertEquals("AGENT_END", eventType(events.get(events.size() - 1)));
    }

    @Test
    @DisplayName("HITL：修改参数后批准 → 工具收到改后参数执行，原参数不出现")
    void hitl_approveWithModifiedArguments() {
        Long sessionId = preparedAskingSession();

        List<String> events = confirmAndCollect(sessionId, "fake-call-0", "confirmable_echo",
                "{\"text\":\"改后的文本\"}", true);

        String toolResult = lastOf(events, "TOOL_RESULT_TEXT_DELTA");
        assertTrue(toolResult != null && toolResult.contains("confirmed-echo:改后的文本"),
                "改参数后批准应以修改后的参数执行，实际：" + toolResult);
        assertFalse(toolResult.contains("敲黑板"), "原始参数不应被执行");
    }

    @Test
    @DisplayName("HITL：拒绝 → 工具不执行，拒绝结果进入上下文驱动下一轮推理")
    void hitl_denySkipsTool() {
        Long sessionId = preparedAskingSession();

        List<String> events = confirmAndCollect(sessionId, "fake-call-0", "confirmable_echo",
                "{\"text\":\"敲黑板\"}", false);
        List<String> types = events.stream().map(this::eventType).toList();

        assertTrue(types.contains("USER_CONFIRM_RESULT"), "拒绝同样以确认结果事件开始");
        assertFalse(String.join("\n", events).contains("confirmed-echo"), "拒绝后工具函数体不得执行");
        assertTrue(fake().getReceivedMessages().size() >= 2, "拒绝后应发起下一轮推理消化拒绝结果");
        String nextRoundContext = io.agentscope.core.util.JsonUtils.getJsonCodec()
                .toJson(fake().getReceivedMessages().get(1));
        assertTrue(nextRoundContext.contains("Permission denied by user"),
                "下一轮推理请求应携带用户拒绝结果");
        assertTrue(lastOf(events, "AGENT_RESULT").contains("工具执行完毕"), "拒绝后推理照常完成");
    }

    @Test
    @DisplayName("HITL：无待确认调用时回应 → 流以 SESSION_ERROR 收尾而非中断连接")
    void hitl_confirmWithoutPendingFails() {
        Long sessionId = createDebugSession(createPublishedSpec(), null);
        collectEvents(sessionId, "普通一轮"); // 正常完成，无挂起

        List<String> events = confirmAndCollect(sessionId, "fake-call-0", "confirmable_echo", "{}", true);

        assertTrue(events.stream().anyMatch(event -> eventType(event).equals("SESSION_ERROR")),
                "无待确认调用时流应以 SESSION_ERROR 收尾，实际："
                        + events.stream().map(this::eventType).toList());
    }

    @Test
    @DisplayName("中断：运行中的流及时收尾（AGENT_END），未跑完脚本，且会话可立即继续")
    void interrupt_stopsRunningStream() throws Exception {
        CURRENT_FAKE.set(FakeChatModel.script()
                .reply("第一段")
                .reply("第二段")
                .reply("第三段")
                .stepDelay(Duration.ofMillis(400))
                .build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        DebugSessionMessageCommand command = new DebugSessionMessageCommand();
        command.setContent("慢慢来");
        sessionController.sendMessage(sessionId, command)
                .doOnNext(events::add)
                .doFinally(signal -> finished.countDown())
                .subscribe();
        awaitFirstEvent(events);

        assertTrue(sessionController.interruptSession(sessionId).getData(), "应命中运行中的流并触发中断");
        assertTrue(finished.await(STREAM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "中断后流应及时终结");
        assertEquals("AGENT_END", eventType(events.get(events.size() - 1)), "中断后事件流应正常收尾");
        String emittedText = events.stream()
                .filter(event -> eventType(event).equals("TEXT_BLOCK_DELTA"))
                .map(event -> textDelta(event)).collect(Collectors.joining());
        assertFalse(emittedText.contains("第三段"), "中断生效后不应继续跑完全部脚本，实际输出：" + emittedText);

        // 中断旗标在下一次调用开始时复位：会话可立即继续新消息
        CURRENT_FAKE.set(FakeChatModel.script().reply("中断后的新答复").build());
        List<String> followUp = collectEvents(sessionId, "继续");
        assertTrue(lastOf(followUp, "AGENT_RESULT").contains("中断后的新答复"), "中断后应能继续对话");
        assertFalse(sessionController.interruptSession(sessionId).getData(),
                "无运行中的流时中断幂等返回 false");
    }

    @Test
    @DisplayName("运行防护：同一会话流运行中再发消息 → 业务错误（同一时刻至多一条流）")
    void sendMessage_rejectsConcurrentStream() throws Exception {
        CURRENT_FAKE.set(FakeChatModel.script()
                .reply("慢答复").reply("慢答复")
                .stepDelay(Duration.ofMillis(400))
                .build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);

        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        DebugSessionMessageCommand command = new DebugSessionMessageCommand();
        command.setContent("第一条");
        sessionController.sendMessage(sessionId, command)
                .doOnNext(events::add)
                .doFinally(signal -> finished.countDown())
                .subscribe();
        awaitFirstEvent(events);

        DebugSessionMessageCommand second = new DebugSessionMessageCommand();
        second.setContent("第二条");
        assertServiceException(() -> sessionController.sendMessage(sessionId, second), SESSION_RUNNING);

        assertTrue(finished.await(STREAM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "首条流应不受影响正常完成");
    }

    @Test
    @DisplayName("克隆重跑：复制对话历史为新调试会话，推理参数覆盖生效、消息轮数归零")
    void cloneSession_copiesHistoryAndOverrides() {
        CURRENT_FAKE.set(FakeChatModel.script().reply("原会话答复").build());
        Long sourceId = createDebugSession(createPublishedSpec(), null);
        collectEvents(sourceId, "原会话问题");

        DebugSessionCloneCommand cloneCommand = new DebugSessionCloneCommand();
        cloneCommand.setMaxIters(9);
        cloneCommand.setTemperature(0.9);
        Long clonedId = sessionController.cloneSession(sourceId, cloneCommand).getData();

        assertNotEquals(sourceId, clonedId);
        SessionDTO cloned = sessionController.getSessionPage(pageOf(null)).getData().getList().stream()
                .filter(session -> session.getId().equals(clonedId)).findFirst().orElseThrow();
        assertEquals(10, cloned.getType(), "克隆产物恒为调试会话");
        assertEquals(0, cloned.getMessageRounds(), "克隆会话消息轮数归零");
        assertEquals(9, cloned.getOverrideMaxIters(), "推理参数覆盖应落库");
        assertEquals(0.9, cloned.getOverrideTemperature());
        assertTrue(cloned.getTitle() != null && cloned.getTitle().contains("（克隆）"),
                "缺省标题应派生自源会话并带克隆标记");

        // 状态复制验证：克隆会话首轮推理请求携带源会话的完整问答历史
        CURRENT_FAKE.set(FakeChatModel.script().reply("克隆后答复").build());
        List<String> events = collectEvents(clonedId, "克隆会话问题");
        String firstRound = io.agentscope.core.util.JsonUtils.getJsonCodec()
                .toJson(fake().getReceivedMessages().get(0));
        assertTrue(firstRound.contains("原会话问题") && firstRound.contains("原会话答复"),
                "克隆会话首轮推理应携带源会话历史（agentscope 状态整体复制）");
        assertTrue(lastOf(events, "AGENT_RESULT").contains("克隆后答复"), "克隆会话应可正常完成推理");
        assertEquals(1, sessionController.getSessionPage(pageOf(null)).getData().getList().stream()
                .filter(session -> session.getId().equals(clonedId))
                .map(SessionDTO::getMessageRounds).findFirst().orElse(-1), "克隆会话轮数从 0 起计");
    }

    // ==================== 测试链路构建与断言辅助 ====================

    /** 建渠道并登记模型，返回模型编号 */
    private Long createChannelAndModel() {
        ChannelCreateCommand channelCommand = new ChannelCreateCommand();
        channelCommand.setName("OpenAI 主渠道");
        channelCommand.setProvider(ChannelProvider.OPENAI.getCode());
        channelCommand.setBaseUrl("https://api.openai.com/v1");
        channelCommand.setApiKey("sk-test-fake");
        Long channelId = channelController.createChannel(channelCommand).getData();

        ModelCreateCommand modelCommand = new ModelCreateCommand();
        modelCommand.setChannelId(channelId);
        modelCommand.setModelId("gpt-4o");
        modelCommand.setName("GPT-4o");
        return modelController.createModel(modelCommand).getData();
    }

    /** 建规格（携带系统提示）并发布，返回规格编号 */
    private Long createPublishedSpec() {
        Long modelId = createChannelAndModel();
        Long specId = agentSpecController.createSpec(specCreateCommand(modelId)).getData();
        agentSpecController.publishSpec(publishCommand(specId));
        return specId;
    }

    private AgentSpecCreateCommand specCreateCommand(Long modelId) {
        AgentSpecCreateCommand command = new AgentSpecCreateCommand();
        command.setName("测试规格");
        // 编码按规格实例唯一（同用例可建多个规格，发布版本递增不换码）
        command.setSpecCode("debug-console-spec-" + SPEC_CODE_SEQ.incrementAndGet());
        command.setDescription("调试台测试规格，验证事件流与装配链");
        command.setSystemPrompt(SYSTEM_PROMPT);
        command.setModelId(modelId);
        return command;
    }

    private AgentSpecPublishCommand publishCommand(Long specId) {
        AgentSpecPublishCommand command = new AgentSpecPublishCommand();
        command.setId(specId);
        return command;
    }

    /** 创建调试会话（versionNo 为空绑定当前默认版本），返回会话编号 */
    private Long createDebugSession(Long specId, Integer versionNo) {
        DebugSessionCreateCommand command = new DebugSessionCreateCommand();
        command.setSpecId(specId);
        command.setVersionNo(versionNo);
        return sessionController.createDebugSession(command).getData();
    }

    /** 发送消息并阻塞收集完整事件流（SSE 端点在生产环境逐条转发，测试直接订阅同一 Flux） */
    private List<String> collectEvents(Long sessionId, String content) {
        DebugSessionMessageCommand command = new DebugSessionMessageCommand();
        command.setContent(content);
        List<String> events = sessionController.sendMessage(sessionId, command)
                .collectList().block(STREAM_TIMEOUT);
        return events == null ? List.of() : events;
    }

    /** 发起一条会触发确认请求的消息并等待挂起轮收尾（脚本第 1 步已消费），返回会话编号 */
    private Long preparedAskingSession() {
        CURRENT_FAKE.set(FakeChatModel.script()
                .callTool("confirmable_echo", Map.of("text", "敲黑板"))
                .reply("工具执行完毕")
                .build());
        Long sessionId = createDebugSession(createPublishedSpec(), null);
        List<String> events = collectEvents(sessionId, "请回显：敲黑板");
        assertTrue(events.stream().anyMatch(event -> eventType(event).equals("REQUIRE_USER_CONFIRM")),
                "前置条件：本轮应触发确认请求挂起");
        return sessionId;
    }

    /** 回应待确认工具调用并阻塞收集后续事件流 */
    private List<String> confirmAndCollect(Long sessionId, String toolCallId, String toolName,
                                           String arguments, boolean approved) {
        DebugSessionConfirmCommand command = new DebugSessionConfirmCommand();
        DebugSessionConfirmCommand.Decision decision = new DebugSessionConfirmCommand.Decision();
        decision.setToolCallId(toolCallId);
        decision.setToolName(toolName);
        decision.setArguments(arguments);
        decision.setApproved(approved);
        command.setDecisions(List.of(decision));
        List<String> events = sessionController.confirmToolCalls(sessionId, command)
                .collectList().block(STREAM_TIMEOUT);
        return events == null ? List.of() : events;
    }

    /** 轮询等待事件流产出首条事件（AGENT_START），保证中断/并发断言不与流启动竞态 */
    private void awaitFirstEvent(List<String> events) throws InterruptedException {
        long deadline = System.currentTimeMillis() + STREAM_TIMEOUT.toMillis();
        while (events.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(events.isEmpty(), "事件流应及时产出首条事件（AGENT_START）");
    }

    /** 取 TEXT_BLOCK_DELTA 事件的增量文本 */
    private String textDelta(String eventJson) {
        Object delta = io.agentscope.core.util.JsonUtils.getJsonCodec()
                .fromJson(eventJson, Map.class).get("delta");
        return delta == null ? "" : String.valueOf(delta);
    }

    private FakeChatModel fake() {
        return CURRENT_FAKE.get();
    }

    private String eventType(String eventJson) {
        Object type = io.agentscope.core.util.JsonUtils.getJsonCodec()
                .fromJson(eventJson, Map.class).get("type");
        return String.valueOf(type);
    }

    /** 取指定类型的最后一条事件 JSON（不存在返回 null） */
    private String lastOf(List<String> events, String type) {
        String result = null;
        for (String event : events) {
            if (eventType(event).equals(type)) {
                result = event;
            }
        }
        return result;
    }

    private SessionPageQuery pageOf(Long specId) {
        SessionPageQuery query = new SessionPageQuery();
        query.setPageNo(1);
        query.setPageSize(100);
        query.setType(10); // 调试会话
        query.setSpecId(specId);
        return query;
    }

}
