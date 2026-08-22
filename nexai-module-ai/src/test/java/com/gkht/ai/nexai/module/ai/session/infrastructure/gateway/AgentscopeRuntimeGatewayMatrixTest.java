package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionCapability;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ExecutionEnvConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import com.gkht.ai.nexai.module.ai.model.infrastructure.gateway.ChatModelFactory;
import com.gkht.ai.nexai.module.ai.session.domain.exception.SessionSandboxUnavailableException;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.framework.config.AiRuntimeProperties;
import com.gkht.ai.nexai.module.ai.support.FakeChatModel;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.harness.agent.HarnessAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规格驱动矩阵测试（ADR-0007 决策 8，M1 回归口径）：ExecutionEnvConfig → HarnessAgent
 * 内置行为开启矩阵。直连网关装配（不继承基类、不碰 DB——模型工厂与状态存储均为替身），
 * 断言装配产物的工具面、workspace 布局与 AGENTS.md 物化。沙箱用例以 docker 探测替身门控，
 * 装配本身不拉起容器（容器在首次调用时才创建），无 docker 环境同样可断言装配形态。
 */
class AgentscopeRuntimeGatewayMatrixTest {

    private static final String SYSTEM_PROMPT = "你是矩阵测试智能体";

    /** 文件六件套 */
    private static final Set<String> FILE_TOOLS = Set.of(
            "read_file", "write_file", "edit_file", "grep_files", "glob_files", "list_files");

    /** memory 四件套（session_search 随 memory 工具组开关） */
    private static final Set<String> MEMORY_TOOLS = Set.of(
            "memory_search", "memory_get", "memory_save", "session_search");

    @TempDir
    Path workspaceRoot;

    private AiRuntimeProperties properties;
    private DockerAvailabilityProbe dockerProbe;
    private AgentscopeRuntimeGateway gateway;

    private HarnessAgent assembled;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        properties = new AiRuntimeProperties();
        properties.getWorkspace().setRoot(workspaceRoot);
        dockerProbe = new DockerAvailabilityProbe();
        // 装配断言不依赖真实 docker（容器在首次调用才创建），探测结果由用例注入
        dockerProbe.override(true);
        gateway = new AgentscopeRuntimeGateway(
                stubModelFactory(),
                new StubStateStoreProvider(),
                properties, new SandboxImageResolver(properties), dockerProbe,
                emptyContributors());
    }

    @AfterEach
    void tearDown() {
        if (assembled != null) {
            assembled.close();
            assembled = null;
        }
        TenantContextHolder.clear();
    }

    // —— 矩阵行 1：无 workspace 规格（纯对话）——

    @Test
    @DisplayName("纯对话规格：文件/execute/memory/transcript 面全禁，spawn 系存在，web 不存在，零落盘")
    void pureChatZeroDisk() {
        assembled = gateway.assemble(config("pure-chat", OwnerLevel.TENANT, null, null,
                ExecutionEnvConfig.disabled(), SYSTEM_PROMPT));

        Set<String> tools = assembled.getToolkit().getToolNames();
        assertTrue(tools.contains("agent_spawn"), "spawn 系应存在（通用断言）");
        assertTrue(tools.contains("agent_list"));
        for (String tool : FILE_TOOLS) {
            assertFalse(tools.contains(tool), "纯对话不应有文件工具：" + tool);
        }
        assertFalse(tools.contains("execute"), "纯对话不应有执行工具");
        for (String tool : MEMORY_TOOLS) {
            assertFalse(tools.contains(tool), "纯对话不应有记忆/检索工具（落盘入口）：" + tool);
        }
        assertWebToolsRemoved(tools);
        // 零落盘：受控根下无任何目录创建
        try (Stream<Path> entries = Files.list(workspaceRoot)) {
            assertEquals(0, entries.count(), "workspace 根应无落盘");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // —— 矩阵行 2：workspace 无沙箱（租户级）——

    @Test
    @DisplayName("workspace 无沙箱（租户级）：文件六件套存在、execute 不存在（本地模式禁执行能力）、"
            + "种子物化与记忆/用户文件分桶开启")
    void workspaceWithoutSandbox() {
        assembled = gateway.assemble(config("tenant-agent", OwnerLevel.TENANT, null, null,
                ExecutionEnvConfig.of(true, false, null), SYSTEM_PROMPT));

        Set<String> tools = assembled.getToolkit().getToolNames();
        FILE_TOOLS.forEach(tool -> assertTrue(tools.contains(tool), "应有文件工具：" + tool));
        assertFalse(tools.contains("execute"), "非沙箱本地模式禁执行能力（宿主 shell 无隔离）");
        MEMORY_TOOLS.forEach(tool -> assertTrue(tools.contains(tool), "应有记忆工具：" + tool));
        assertTrue(tools.contains("agent_spawn"));
        assertWebToolsRemoved(tools);

        // workspace 布局：租户级 {root}/t{tenantId}/{specCode}/
        Path workspace = workspaceRoot.resolve("t1").resolve("tenant-agent");
        assertTrue(Files.isDirectory(workspace), "workspace 应按归属层级落盘：" + workspace);
        // AGENTS.md 物化 = 快照 systemPrompt
        assertAgentsMd(workspace, SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("AGENTS.md 物化内容比对：快照变更后新装配覆写，DB 快照是唯一权威源")
    void agentsMdMaterializeWithContentCompare() throws Exception {
        gateway.assemble(config("materialize", OwnerLevel.TENANT, null, null,
                ExecutionEnvConfig.of(true, false, null), "v1 人格")).close();

        // 新版本人格发布后，下一次装配覆写
        gateway.assemble(config("materialize", OwnerLevel.TENANT, null, null,
                ExecutionEnvConfig.of(true, false, null), "v2 人格")).close();
        assertEquals("v2 人格", Files.readString(
                workspaceRoot.resolve("t1").resolve("materialize").resolve("AGENTS.md")));

        // 内容相同时跳过（不破坏用户桶内文件——种子区覆写，用户区不动）
        Path agentsMd = workspaceRoot.resolve("t1").resolve("materialize").resolve("AGENTS.md");
        long before = Files.getLastModifiedTime(agentsMd).toMillis();
        Thread.sleep(5);
        gateway.assemble(config("materialize", OwnerLevel.TENANT, null, null,
                ExecutionEnvConfig.of(true, false, null), "v2 人格")).close();
        assertEquals(before, Files.getLastModifiedTime(agentsMd).toMillis(),
                "内容相同应跳过覆写（物化缓存命中）");
    }

    // —— 矩阵行 3：workspace + 沙箱 —— docker 门控经探测替身（装配不拉容器）——

    @Test
    @DisplayName("workspace+沙箱规格：文件与 execute 工具存在（装配形态断言，容器首次调用才创建）")
    void workspaceWithSandbox() {
        assembled = gateway.assemble(config("sandboxed", OwnerLevel.TENANT, null, null,
                ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.PYTHON)),
                SYSTEM_PROMPT));

        Set<String> tools = assembled.getToolkit().getToolNames();
        FILE_TOOLS.forEach(tool -> assertTrue(tools.contains(tool), "沙箱模式应有文件工具：" + tool));
        assertTrue(tools.contains("execute"), "沙箱模式应开放执行工具");
        MEMORY_TOOLS.forEach(tool -> assertTrue(tools.contains(tool)));
        assertWebToolsRemoved(tools);
        assertAgentsMd(workspaceRoot.resolve("t1").resolve("sandboxed"), SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("环境无 docker 而规格要求沙箱：装配显式报错（不静默降级）")
    void sandboxWithoutDockerFails() {
        dockerProbe.override(false);
        assertThrows(SessionSandboxUnavailableException.class, () -> gateway.assemble(
                config("sandboxed", OwnerLevel.TENANT, null, null,
                        ExecutionEnvConfig.of(true, true, List.of(ExecutionCapability.SHELL)),
                        SYSTEM_PROMPT)));
    }

    // —— 矩阵行 5：用户级规格 ——

    @Test
    @DisplayName("用户级规格：workspace 落 u{userId}/{specCode}/ 树下（物理目录即隔离，AGENT scope）")
    void userLevelWorkspaceLayout() {
        assembled = gateway.assemble(config("personal", OwnerLevel.USER, 7L, null,
                ExecutionEnvConfig.of(true, false, null), SYSTEM_PROMPT));

        Path workspace = workspaceRoot.resolve("t1").resolve("u7").resolve("personal");
        assertTrue(Files.isDirectory(workspace), "用户级 workspace 应挂归属用户树下：" + workspace);
        assertAgentsMd(workspace, SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("平台级布局与 agentName 形态（{spec_code}-v{versionNo}）由装配链保证")
    void agentNameAndPlatformLayout() {
        assembled = gateway.assemble(config("platform-tool", OwnerLevel.PLATFORM, null, 3,
                ExecutionEnvConfig.of(true, false, null), SYSTEM_PROMPT));

        assertEquals("platform-tool-v3", assembled.getName());
        // 平台级布局：{root}/platform/{specCode}/（M1 无入口创建平台级数据，装配链先行支持）
        assertTrue(Files.isDirectory(workspaceRoot.resolve("platform").resolve("platform-tool")));
    }

    private void assertWebToolsRemoved(Set<String> tools) {
        assertFalse(tools.contains("web_fetch"), "web_fetch 应被显式移除");
        assertFalse(tools.contains("web_search"), "web_search 应被显式移除");
    }

    private void assertAgentsMd(Path workspace, String expectedPrompt) {
        Path agentsMd = workspace.resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd), "AGENTS.md 应已物化：" + agentsMd);
        try {
            assertEquals(expectedPrompt, Files.readString(agentsMd),
                    "AGENTS.md 注入内容 = 快照 systemPrompt");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 构建装配指令（矩阵各维度：specCode / 归属层级 / 归属用户 / 版本号 / 执行环境 / 系统提示） */
    private AgentRuntimeConfig config(String specCode, OwnerLevel ownerLevel, Long ownerUserId,
                                      Integer versionNo, ExecutionEnvConfig env, String systemPrompt) {
        Channel channel = Channel.create("openai 渠道", ChannelProvider.OPENAI, "https://api.test/v1",
                "sk-test", ChannelOwnerType.TENANT);
        // channel 未落库 id 为 null，模型引用用固定渠道号（矩阵装配不触达渠道持久化）
        Model model = Model.create(1L, "gpt-test", "矩阵测试模型", 128_000, null, null, null);
        return AgentRuntimeConfig.of("sess-" + specCode, "9", specCode + "-v" + (versionNo == null ? 1 : versionNo),
                specCode, ownerLevel, ownerUserId, systemPrompt, 5, null,
                env, channel, model);
    }

    /** 模型工厂替身：矩阵装配只注册名字，不真实外呼 */
    private static ChatModelFactory stubModelFactory() {
        return new ChatModelFactory() {
            @Override
            public io.agentscope.core.model.Model create(Channel channel, String modelId) {
                return FakeChatModel.script().reply("ok").build();
            }
        };
    }

    /** 空贡献者（矩阵断言不依赖平台工具注入） */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<RuntimeToolContributor> emptyContributors() {
        ObjectProvider<RuntimeToolContributor> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.orderedStream()).thenReturn(java.util.stream.Stream.empty());
        return provider;
    }

    /** 状态存储替身：内存实现（矩阵装配不触达持久化） */
    private static class StubStateStoreProvider extends AgentStateStoreProvider {
        @Override
        public io.agentscope.core.state.AgentStateStore get() {
            return new InMemoryAgentStateStore();
        }
    }

}
