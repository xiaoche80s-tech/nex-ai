package com.gkht.ai.nexai.module.ai.session.interfaces.controller.admin.session;

import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverterImpl;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
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
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
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
        TenantDbTestConfiguration.class, DebugSessionControllerTest.StubRepositoryConfiguration.class,
        DebugSessionControllerTest.FakeRuntimeGatewayConfiguration.class})
public class DebugSessionControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;

    @Resource
    private DebugSessionController debugSessionController;

    @Resource
    private SessionService sessionService;

    @Resource
    private AgentRuntimeGateway runtimeGateway;

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

    /** 规格/渠道仓储桩（TestConfiguration bean）：返回固定规格与渠道模型，不外呼 DB 业务 */
    @TestConfiguration
    static class StubRepositoryConfiguration {

        @Bean
        public AgentSpecRepository agentSpecRepository() {
            return new AgentSpecRepository() {
                @Override
                public Long save(AgentSpec spec) {
                    return 1L;
                }

                @Override
                public AgentSpec findById(Long id) {
                    if (!Long.valueOf(1L).equals(id)) {
                        return null;
                    }
                    AgentSpecConfig config = AgentSpecConfig.of(1L, null, "系统提示", null,
                            null, List.of(), List.of(), null);
                    return AgentSpec.reconstitute(1L, "测试规格", "test-spec", null,
                            OwnerLevel.TENANT, null, config, 1, null);
                }

                @Override
                public Integer findMaxVersionNo(Long specId) {
                    return 1;
                }

                @Override
                public Long saveVersion(com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion version) {
                    return 1L;
                }

                @Override
                public List<com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion> listVersions(Long specId) {
                    AgentSpecConfig config = AgentSpecConfig.of(1L, null, "系统提示", null,
                            null, List.of(), List.of(), null);
                    return List.of(com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion
                            .reconstitute(1L, 1L, 1, config, null, null));
                }

                @Override
                public boolean existsVersion(Long specId, int versionNo) {
                    return versionNo == 1;
                }

                @Override
                public void update(AgentSpec spec) {
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
    }

    /** 运行时网关桩：记录 chat 调用参数并返回固定事件流（不外呼 agentscope） */
    @TestConfiguration
    static class FakeRuntimeGatewayConfiguration {

        @Bean
        public AgentRuntimeGateway agentRuntimeGateway() {
            return new AgentRuntimeGateway() {
                final AtomicReference<String> lastContent = new AtomicReference<>();

                @Override
                public Flux<RuntimeEvent> chat(AgentRuntimeConfig config, String content) {
                    lastContent.set(content);
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
