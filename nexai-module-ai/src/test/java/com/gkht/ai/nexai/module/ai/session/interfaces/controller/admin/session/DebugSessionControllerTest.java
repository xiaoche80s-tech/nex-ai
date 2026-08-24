package com.gkht.ai.nexai.module.ai.session.interfaces.controller.admin.session;

import com.gkht.ai.nexai.module.system.api.user.AdminUserApi;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolMount;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.repository.McpServerRepository;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpOwnerType;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport;
import com.gkht.ai.nexai.module.ai.session.application.service.SessionService;
import com.gkht.ai.nexai.module.ai.session.application.service.SessionServiceImpl;
import com.gkht.ai.nexai.module.ai.session.domain.gateway.AgentRuntimeGateway;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ToolCallDecision;
import com.gkht.ai.nexai.module.ai.session.infrastructure.converter.SessionConverterImpl;
import com.gkht.ai.nexai.module.ai.session.infrastructure.mapper.SessionMapper;
import com.gkht.ai.nexai.module.ai.session.infrastructure.repository.SessionRepositoryImpl;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMaterializationGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASSEMBLE_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调试会话 HTTP 契约测试（H2 全链路，AgentRuntimeGateway stub 不外呼）：
 * 创建会话（绑定规格/生成业务键）、发消息（SSE 事件流桥接）、HITL 审批编排、
 * 挂起上下文解析、列表/详情、异常路径（规格不存在/会话不存在）。
 * 网关真实行为由 PG 直连接缝测试（AgentscopeRuntimeGatewayTest）保障。
 */
@Import({DebugSessionController.class, SessionServiceImpl.class, SessionRepositoryImpl.class,
        SessionConverterImpl.class, SessionMapper.class,
        com.gkht.ai.nexai.module.ai.session.application.service.AgentRuntimeAssembler.class,
        com.gkht.ai.nexai.module.ai.agentspec.application.service.AgentSpecServiceImpl.class,
        com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl.class,
        TenantDbTestConfiguration.class, DebugSessionControllerTest.StubRepositoryConfiguration.class,
        DebugSessionControllerTest.FakeRuntimeGatewayConfiguration.class})
public class DebugSessionControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;

    @Resource
    private DebugSessionController debugSessionController;

    @Resource
    private SessionService sessionService;

    /** AgentSpecServiceImpl 依赖发布人解析（system api），单测上下文无 system 模块，mock 之（工单 25） */
    @MockitoBean
    private AdminUserApi adminUserApi;

    @Resource
    private AgentRuntimeGateway runtimeGateway;

    /** 捕获最近一次 chat 的装配指令（挂载解析断言用，工单 12/13） */
    static final AtomicReference<AgentRuntimeConfig> LAST_CONFIG = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        TenantContextHolderStub.set(TENANT_ONE);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolderStub.clear();
    }

    @Test
    @DisplayName("发起调试会话：绑定规格生成业务键，列表可见")
    public void createDebugSessionBindsSpec() {
        Long id = sessionService.createDebugSession(createCommand(), 1L);
        assertNotNull(id);

        var page = sessionService.getSessionPage(new com.gkht.ai.nexai.module.ai.session.application.query.SessionPageQuery());
        assertTrue(page.getTotal() >= 1);
        assertEquals("调试会话标题", page.getList().get(0).getTitle());
    }

    @Test
    @DisplayName("发起调试会话：规格不存在报业务异常")
    public void createDebugSessionRequiresSpec() {
        var command = createCommand();
        command.setSpecId(99999L);
        assertServiceException(() -> sessionService.createDebugSession(command, 1L),
                AGENT_SPEC_NOT_EXISTS);
    }

    @Test
    @DisplayName("发送消息：SSE 事件流桥接（stub 网关返回固定事件）")
    public void sendDebugMessageBridgesEvents() {
        Long id = sessionService.createDebugSession(createCommand(), 1L);
        var message = new com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand();
        message.setContent("你好");

        var events = sessionService.sendDebugMessage(id, message, 1L).collectList().block();
        assertNotNull(events);
        assertEquals(3, events.size(), "stub 网关返回 AGENT_START + TEXT_BLOCK_DELTA + AGENT_END");
        assertEquals(com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType.AGENT_START,
                events.get(0).type());
        assertEquals(com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType.AGENT_END,
                events.get(2).type());
    }

    @Test
    @DisplayName("发送消息：会话不存在报业务异常")
    public void sendDebugMessageRequiresSession() {
        var message = new com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand();
        message.setContent("你好");
        assertServiceException(() -> sessionService.sendDebugMessage(99999L, message, 1L),
                SESSION_NOT_EXISTS);
    }

    @Test
    @DisplayName("会话详情：补充规格业务编码")
    public void getSessionFillsSpecCode() {
        Long id = sessionService.createDebugSession(createCommand(), 1L);
        var dto = sessionService.getSession(id);
        assertEquals("test-spec", dto.getSpecCode());
        assertEquals("DEBUG", dto.getType());
    }

    @Test
    @DisplayName("挂载解析（工单 12/13）：技能引用物化为目录分组，MCP 挂载解析为聚合本体")
    public void assembleRuntimeResolvesMounts() {
        Long id = sessionService.createDebugSession(createCommand(), 1L);
        var message = new com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand();
        message.setContent("你好");
        sessionService.sendDebugMessage(id, message, 1L).collectList().block();

        AgentRuntimeConfig config = LAST_CONFIG.get();
        assertNotNull(config);
        // 技能引用 → 物化目录分组（stub 网关返回 /tmp/fake-skills/t1/skills/{name}，父目录分组）
        assertEquals(1, config.getSkillMounts().size());
        assertEquals("order-helper", config.getSkillMounts().get(0).getSkillNames().get(0));
        assertTrue(config.getSkillMounts().get(0).getBaseDir().endsWith("skills"));
        assertEquals("5@1", config.getSkillMounts().get(0).getFingerprint());
        // MCP 挂载 → 聚合本体（连接配置直接可用）
        assertEquals(1, config.getMcpServers().size());
        assertEquals(7L, config.getMcpServers().get(0).getId());
        assertEquals("file-tools", config.getMcpServers().get(0).getName());
    }

    @Test
    @DisplayName("挂载解析：MCP Server 停用为配置性缺失，装配显式报错（不静默降级）")
    public void assembleRuntimeRejectsDisabledMcpServer() {
        try {
            StubRepositoryConfiguration.MCP_ENABLED.set(Boolean.FALSE);
            Long id = sessionService.createDebugSession(createCommand(), 1L);
            var message = new com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionMessageCommand();
            message.setContent("你好");
            assertServiceException(() -> sessionService.sendDebugMessage(id, message, 1L)
                    .collectList().block(), SESSION_ASSEMBLE_INVALID, "挂载的 MCP Server 已停用（file-tools）");
        } finally {
            StubRepositoryConfiguration.MCP_ENABLED.set(Boolean.TRUE);
        }
    }

    /** 创建命令（绑定 stub 规格） */
    private com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand createCommand() {
        var command = new com.gkht.ai.nexai.module.ai.session.application.command.DebugSessionCreateCommand();
        command.setTitle("调试会话标题");
        command.setSpecId(1L);
        return command;
    }

    /** 租户上下文（H2 测试沿用芋道 TenantContextHolder，简单封装为静态） */
    static class TenantContextHolderStub {
        static void set(Long tenantId) {
            com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder.setTenantId(tenantId);
        }

        static void clear() {
            com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder.clear();
        }
    }

    /** 规格/渠道/技能/MCP 仓储桩（TestConfiguration bean）：返回固定聚合与模型，不外呼 DB 业务。
     *  规格版本快照携带挂载（skillIds=[5] + ToolMount(MCP, 7)），供挂载解析断言。 */
    @TestConfiguration
    static class StubRepositoryConfiguration {

        /** 挂载解析用固定配置：技能引用 5 + MCP Server 挂载 7 */
        static AgentSpecConfig mountedConfig() {
            return AgentSpecConfig.of(1L, null, "系统提示", null,
                    null, List.of(5L),
                    List.of(ToolMount.of(ToolSource.MCP, 7L, List.of(), List.of())), null, null);
        }

        @Bean
        public AgentSpecRepository agentSpecRepository() {
            return new AgentSpecRepository() {
                @Override
                public Long save(AgentSpec spec) {
                    return spec.getId() == null ? 1L : spec.getId();
                }

                @Override
                public com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec findBySpecCode(String specCode) {
                    return null;
                }

                @Override
                public AgentSpec findById(Long id) {
                    if (!Long.valueOf(1L).equals(id)) {
                        return null;
                    }
                    return AgentSpec.reconstitute(1L, "测试规格", "test-spec", null,
                            OwnerLevel.TENANT, null, mountedConfig(), 1, null);
                }

                @Override
                public List<com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion> listVersions(Long specId) {
                    return List.of(com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion
                            .reconstitute(1L, 1L, 1, mountedConfig(), null, null));
                }

                @Override
                public com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion findVersion(
                        Long specId, Integer versionNo) {
                    return Integer.valueOf(1).equals(versionNo)
                            ? com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion
                                    .reconstitute(1L, 1L, 1, mountedConfig(), null, null)
                            : null;
                }

                @Override
                public Integer persistPublication(AgentSpec spec, String note) {
                    return 1;
                }
            };
        }

        @Bean
        public ChannelRepository channelRepository() {
            return new ChannelRepository() {
                @Override
                public Long save(Channel channel) {
                    return 1L;
                }

                @Override
                public Channel findById(Long id) {
                    return Channel.create("test-channel", ChannelProvider.OPENAI_COMPAT,
                            "https://api.test.local/v1", "sk-test", ChannelOwnerType.TENANT);
                }

                @Override
                public void deleteByIdCascade(Long id) {
                }

                @Override
                public Long saveModel(Model model) {
                    return 1L;
                }

                @Override
                public Model findModelById(Long id) {
                    return Model.create(1L, "fake-model", "测试模型", 8192, null, null);
                }

                @Override
                public void deleteModelById(Long id) {
                }
            };
        }

        /** 技能仓储桩：编号 5 返回带当前版本（v1）的技能，其余 null */
        @Bean
        public SkillRepository skillRepository() {
            return new SkillRepository() {
                @Override
                public Skill findById(Long id) {
                    return Long.valueOf(5L).equals(id)
                            ? Skill.reconstitute(5L, "order-helper", "订单技能",
                                    SkillOwnerLevel.TENANT, null, 1, null)
                            : null;
                }

                @Override
                public List<SkillVersion> listVersions(Long skillId) {
                    return List.of(SkillVersion.reconstitute(1L, 5L, 1,
                            SkillContent.of("---\nname: order-helper\ndescription: 订单技能\n---\n# 订单技能",
                                    null), null, null));
                }

                // —— 以下桩无关本测试路径 ——

                @Override
                public Long save(Skill skill) {
                    return 5L;
                }

                @Override
                public void deleteByIdCascade(Long id) {
                }

                @Override
                public Long saveVersion(SkillVersion version) {
                    return 1L;
                }

                @Override
                public Integer findMaxVersionNo(Long skillId) {
                    return 1;
                }

                @Override
                public void update(Skill skill) {
                }
            };
        }

        /** 物化网关桩：返回固定物化目录（真实落盘契约由物化网关测试保障） */
        @Bean
        public SkillMaterializationGateway skillMaterializationGateway() {
            return (skill, tenantId, content) -> "/tmp/fake-skills/t" + tenantId + "/skills/" + skill.getName();
        }

        /** MCP Server 仓储桩：编号 7 返回启用中的 Server（停用态由用例内 flag 切换） */
        static final AtomicReference<Boolean> MCP_ENABLED = new AtomicReference<>(Boolean.TRUE);

        @Bean
        public McpServerRepository mcpServerRepository() {
            return new McpServerRepository() {
                @Override
                public McpServer findById(Long id) {
                    if (!Long.valueOf(7L).equals(id)) {
                        return null;
                    }
                    return McpServer.reconstitute(7L, "file-tools", McpTransport.STREAMABLE_HTTP,
                            "http://mcp.test.local/mcp", null, List.of(), java.util.Map.of(),
                            java.util.Map.of(), null, List.of(), List.of(),
                            Boolean.TRUE.equals(MCP_ENABLED.get()), McpOwnerType.TENANT, null, null);
                }

                @Override
                public Long save(McpServer server) {
                    return 7L;
                }

                @Override
                public void deleteById(Long id) {
                }
            };
        }
    }

    /** 运行时网关桩：记录 chat 调用参数并返回固定事件流（不外呼 agentscope） */
    @TestConfiguration
    static class FakeRuntimeGatewayConfiguration {

        @Bean
        public AgentRuntimeGateway agentRuntimeGateway() {
            return new AgentRuntimeGateway() {
                final AtomicReference<String> lastContent = new AtomicReference<>();

                @Override
                public Flux<RuntimeEvent> chatOpenAi(AgentRuntimeConfig config,
                        java.util.List<com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput> messages,
                        String requestId) {
                    return Flux.empty();
                }

                @Override
                public Flux<RuntimeEvent> chat(AgentRuntimeConfig config, String content) {
                    lastContent.set(content);
                    LAST_CONFIG.set(config);
                    return Flux.just(RuntimeEvent.of(RuntimeEventType.AGENT_START,
                                    "{\"type\":\"AGENT_START\"}"),
                            RuntimeEvent.of(RuntimeEventType.TEXT_BLOCK_DELTA,
                                    "{\"type\":\"TEXT_BLOCK_DELTA\",\"delta\":\"你好\"}"),
                            RuntimeEvent.of(RuntimeEventType.AGENT_END,
                                    "{\"type\":\"AGENT_END\"}"));
                }

                @Override
                public Flux<RuntimeEvent> confirmToolCalls(AgentRuntimeConfig config,
                                                          List<ToolCallDecision> decisions) {
                    return Flux.just(RuntimeEvent.of(RuntimeEventType.USER_CONFIRM_RESULT,
                            "{\"type\":\"USER_CONFIRM_RESULT\"}"));
                }

                @Override
                public boolean interrupt(AgentRuntimeConfig config) {
                    return true;
                }

                @Override
                public List<String> listWorkspaceFiles(AgentRuntimeConfig config, String relativePath) {
                    return List.of();
                }

                @Override
                public String readWorkspaceFile(AgentRuntimeConfig config, String relativePath) {
                    return null;
                }

                @Override
                public List<Map<String, Object>> loadSessionMessages(AgentRuntimeConfig config) {
                    return List.of();
                }

                @Override
                public void closeAll() {
                }
            };
        }
    }

}
