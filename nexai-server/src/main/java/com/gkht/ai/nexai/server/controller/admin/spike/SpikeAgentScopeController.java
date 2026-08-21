package com.gkht.ai.nexai.server.controller.admin.spike;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.state.VersionedState;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.extensions.redis.state.RedisAgentStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gkht.ai.nexai.framework.common.pojo.CommonResult;

/**
 * 集成 spike 临时端点（工单 01）：验证 agentscope-java 2.0.x 嵌入 nexai-server 的三大集成风险。
 *
 * 验证矩阵：
 * 1. GET /admin-api/ai/spike/jackson —— Jackson 2（agentscope，com.fasterxml）/ Jackson 3（Spring HTTP 层，tools.jackson）
 *    双栈对 AgentEvent 的序列化对比与往返保真（ADR-0005 的实证依据）；
 * 2. GET /admin-api/ai/spike/sse —— 真实模型调用，Reactor Flux&lt;AgentEvent&gt; 经 Spring MVC 桥接为 SSE，
 *    原生事件 JSON 保真转发（凭据经环境变量 SPIKE_AI_* 注入，代码与配置零硬编码）；
 * 3. GET /admin-api/ai/spike/state —— AgentStateStore 存取冒烟（pg / redis / memory 三引擎）。
 *
 * 【生命周期】spike 专用：M1 工单 06 建立正式运行时装配后整体删除，勿在其上叠功能。
 */
@Tag(name = "AI 平台 - 集成 Spike（临时）")
@RestController
@RequestMapping("/ai/spike")
public class SpikeAgentScopeController {

    /** 状态条目 key：与 ReActAgent 内部持久化 agent_state 使用的 key 对齐 */
    private static final String STATE_KEY = "agent_state";
    private static final String SPIKE_USER_ID = "spike-user";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Resource
    private DataSource dataSource;
    @Resource
    private RedissonClient redissonClient;

    // ==================== 验证 1：Jackson 2/3 双栈序列化 ====================

    @GetMapping("/jackson")
    @Operation(summary = "Jackson 双栈对比：AgentEvent 经 Jackson 2 与 Jackson 3 的序列化与往返")
    public CommonResult<List<Map<String, Object>>> jackson() {
        List<AgentEvent> samples = List.of(
                new AgentStartEvent("spike-session", "reply-001", "SpikeAgent"),
                new TextBlockDeltaEvent("reply-001", "block-001", "你好，"),
                new ModelCallEndEvent("reply-001", new ChatUsage(12, 34, 0.5)));
        List<Map<String, Object>> results = new ArrayList<>();
        for (AgentEvent event : samples) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventClass", event.getClass().getSimpleName());
            // 栈 A：agentscope 自带 codec（Jackson 2 databind，事件注解的原生目标栈）
            try {
                row.put("jackson2", JsonUtils.getJsonCodec().toJson(event));
            } catch (Exception ex) {
                row.put("jackson2Error", ex.toString());
            }
            // 栈 B：nexai-common JsonUtils（Jackson 3 / tools.jackson，与 Spring MVC HTTP 层同栈）
            try {
                String json3 = com.gkht.ai.nexai.framework.common.util.json.JsonUtils.toJsonString(event);
                row.put("jackson3", json3);
                // 判别字段：@JsonTypeInfo(property="type") 是多态反序列化的生命线，两栈都必须保留
                row.put("typeInJackson3", json3 != null && json3.contains("\"type\""));
                // 往返：Jackson 3 序列化的 JSON 能否经 Jackson 3 还原为正确的 AgentEvent 子类
                try {
                    AgentEvent back = com.gkht.ai.nexai.framework.common.util.json.JsonUtils
                            .parseObject(json3, AgentEvent.class);
                    row.put("roundTripOk", back != null && back.getClass() == event.getClass()
                            && back.getType() == event.getType());
                    row.put("roundTripClass", back == null ? null : back.getClass().getSimpleName());
                } catch (Exception ex) {
                    row.put("roundTripError", ex.toString());
                }
            } catch (Exception ex) {
                row.put("jackson3Error", ex.toString());
            }
            results.add(row);
        }
        return CommonResult.success(results);
    }

    // ==================== 验证 2：SSE 返回真实 AgentEvent 流 ====================

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "真实模型 SSE：streamEvents 输出原生 AgentEvent JSON（凭据经环境变量注入）")
    @Parameter(name = "store", description = "状态存储引擎：pg / redis / memory")
    public Flux<String> sse(@RequestParam(value = "prompt", defaultValue = "用一句话介绍你自己，并说出今天的暗号：蓝鲸。") String prompt,
                            @RequestParam(value = "sessionId", defaultValue = "") String sessionId,
                            @RequestParam(value = "store", defaultValue = "pg") String store) {
        String apiKey = System.getenv("SPIKE_AI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.just("{\"spikeError\":\"缺少环境变量 SPIKE_AI_API_KEY（可选 SPIKE_AI_BASE_URL / SPIKE_AI_MODEL），"
                    + "凭据不入库不入 git，仅经环境变量注入\"}");
        }
        String baseUrl = envOr("SPIKE_AI_BASE_URL", "https://api.deepseek.com");
        String model = envOr("SPIKE_AI_MODEL", "deepseek-v4-flash");
        String sid = sessionId.isBlank() ? "spike-" + LocalDateTime.now().format(TS) : sessionId;

        // 每请求新建 Model 与 Agent（agentscope 线程模型：单 agent 单 session 串行）
        OpenAIChatModel chatModel = OpenAIChatModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName(model).stream(true)
                .build();
        ReActAgent agent = ReActAgent.builder()
                .name("SpikeAgent")
                .sysPrompt("你是 NexAI 集成验证智能体，请简洁作答。")
                .model(chatModel)
                .stateStore(buildStore(store))
                .defaultSessionId(sid)
                .build();
        return agent.streamEvents(prompt)
                .subscribeOn(Schedulers.boundedElastic())
                // 保真优先：逐事件经 agentscope 原生 codec（Jackson 2）转 JSON 转发，不做协议改写
                .map(event -> JsonUtils.getJsonCodec().toJson(event))
                .doFinally(signal -> {
                    agent.close();
                    // 注意：Redis 实现的 close() 会连带关闭传入的 RedissonClient（容器共享），故不关闭；
                    // PG 实现的 close() 不关外部 DataSource，关掉无副作用
                });
    }

    // ==================== 验证 3：会话状态存取冒烟 ====================

    @GetMapping("/state")
    @Operation(summary = "状态存取冒烟：save/get/exists/listSessionIds/delete 全链路（不依赖模型）")
    @Parameter(name = "store", description = "状态存储引擎：pg / redis / memory")
    public CommonResult<Map<String, Object>> state(@RequestParam(value = "store", defaultValue = "pg") String store) {
        Map<String, Object> report = new LinkedHashMap<>();
        AgentStateStore stateStore = null;
        try {
            stateStore = buildStore(store);
            report.put("engine", stateStore.getClass().getName());

            String sessionId = "spike-session-" + LocalDateTime.now().format(TS);
            AgentState state = AgentState.builder()
                    .sessionId(sessionId)
                    .userId(SPIKE_USER_ID)
                    .summary("nexai spike 冒烟状态")
                    .context(List.of(new UserMessage(SPIKE_USER_ID, "记住暗号：蓝鲸")))
                    .build();
            // 1. save
            stateStore.save(SPIKE_USER_ID, sessionId, STATE_KEY, state);
            report.put("saveOk", true);
            // 2. get + 内容比对
            Optional<AgentState> loaded = stateStore.get(SPIKE_USER_ID, sessionId, STATE_KEY, AgentState.class);
            boolean getOk = loaded.isPresent()
                    && "nexai spike 冒烟状态".equals(loaded.get().getSummary())
                    && loaded.get().getContext().size() == 1;
            report.put("getOk", getOk);
            // 3. 乐观锁版本（supportsVersioning/getVersioned 为 2.0.3 主干新增 API）
            report.put("supportsVersioning", stateStore.supportsVersioning());
            if (stateStore.supportsVersioning()) {
                VersionedState<AgentState> versioned =
                        stateStore.getVersioned(SPIKE_USER_ID, sessionId, STATE_KEY, AgentState.class);
                report.put("versionedOk", versioned.isPresent() && versioned.value() != null
                        && versioned.version() >= 0);
                report.put("version", versioned.version());
            }
            // 4. exists / listSessionIds
            report.put("existsOk", stateStore.exists(SPIKE_USER_ID, sessionId));
            report.put("listSessionIdsOk", stateStore.listSessionIds(SPIKE_USER_ID).contains(sessionId));
            // 5. delete 后清理干净
            stateStore.delete(SPIKE_USER_ID, sessionId);
            report.put("deleteOk", !stateStore.exists(SPIKE_USER_ID, sessionId));
            report.put("pass", report.values().stream()
                    .filter(v -> v instanceof Boolean).allMatch(v -> (Boolean) v));
        } catch (Exception ex) {
            report.put("pass", false);
            report.put("error", ex.toString());
        } finally {
            // 同 SSE 端点：PG store 可安全 close；Redis store 的 close 会关闭共享 RedissonClient，禁止调用
            if (stateStore != null && !(stateStore instanceof RedisAgentStateStore)) {
                stateStore.close();
            }
        }
        return CommonResult.success(report);
    }

    /** 按引擎参数构建状态存储；pg 首次调用会在共享库自动创建独立 schema agentscope（无业务表侵入） */
    private AgentStateStore buildStore(String store) {
        return switch (store) {
            case "redis" -> RedisAgentStateStore.builder()
                    .redissonClient(redissonClient)
                    .keyPrefix("nexai:spike:session:")
                    .build();
            case "memory" -> new InMemoryAgentStateStore();
            default -> new PostgresAgentStateStore(dataSource, true);
        };
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
