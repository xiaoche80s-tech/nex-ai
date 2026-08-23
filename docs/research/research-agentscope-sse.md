# AgentScope Java：SSE 调用方式调研（流式返回用户请求）

> 调研日期：2026-08-22
>
> 调研对象（一手来源，均为本机路径）：
> - `/Users/jerry/agent-project/agentscope-java/agentscope-examples/`（示例代码）
> - `/Users/jerry/agent-project/agentscope-java/agentscope-core/`（框架核心源码）
> - `/Users/jerry/agent-project/agentscope-java/agentscope-extensions/`（协议扩展与 Spring Boot Starter 源码）
> - `/Users/jerry/agent-project/agentscope-java/.qoder/repowiki/`（框架中文 wiki 文档）
>
> 方法：全部结论回溯到仓库一手源码，引用格式为 `路径:行号`。

**TL;DR**

1. AgentScope Java **没有独立的内置 HTTP server**，对外提供 SSE 服务一律通过 **Spring Boot（MVC 或 WebFlux）**；核心 API 是 `ReActAgent.streamEvents()` 返回 `Flux<AgentEvent>`，由 Web 层转成 SSE。
2. 官方共 **四条 SSE 通道**：① 自研 Spring SSE 端点（最简）② **AG-UI 协议 Starter**（`POST /agui/run`，官方主推、事件语义最全）③ Chat Completions Starter（`POST /v1/chat/completions`，OpenAI 兼容 + `data: [DONE]`）④ Agent Protocol 扩展（`POST /tasks` + `GET /tasks/{id}/events`，支持断线续传）。
3. AG-UI 事件为 `data: {"type":"TEXT_MESSAGE_CONTENT",...,"delta":"..."}` 形式的 JSON，`RUN_STARTED` 开始、`RUN_FINISHED`/`RUN_ERROR` 结束；无内建心跳。
4. 前端消费用 `fetch` POST + `Accept: text/event-stream` 手工解析 `data:` 行（示例 `agui-client.js`），或直接用 CopilotKit v2 React SDK。
5. 对 NexAI 的推荐：**服务端引入 `agentscope-agui-spring-boot-starter` 走 AG-UI 协议**；Java 内部调用或简单场景用自研 `Flux<ServerSentEvent>` 端点。

---

## 1. 总体架构：SSE 服务是怎么提供的

AgentScope Java 对外提供 SSE 的方式**只有一种载体——Spring Boot Web 应用**，没有独立的内置 HTTP server（`agentscope-runtime` 之类的独立服务模块实际是分布式多平面架构 Gateway/Data Plane，同样基于 Spring Web，见 `agentscope-service/`）。四种官方通道的差异只在「协议与端点」上：

| 方案 | 端点 | 请求方式 | 依赖模块 | 适用场景 |
|---|---|---|---|---|
| A. 自研 SSE 端点 | 自定义（如 `GET /chat`） | GET 查询参数 | `agentscope-core` + Spring WebFlux | 最小可用、内部调用 |
| B. AG-UI 协议 | `POST /agui/run`、`POST /agui/run/{agentId}` | POST JSON | `agentscope-agui-spring-boot-starter` | 面向前端的实时 Agent 交互（官方主推） |
| C. Chat Completions | `POST /v1/chat/completions` | POST JSON（OpenAI 格式） | `agentscope-chat-completions-web-starter` | OpenAI 客户端零改造接入 |
| D. Agent Protocol | `POST /tasks` + `GET /tasks/{taskId}/events` | 先建任务再订阅 | `agentscope-extensions-agent-protocol` | 内部任务协议、断线续传 |

（来源：`agentscope-extensions/agentscope-extensions-protocol/` 目录清单；各端点源码见下文）

底层统一链路：`HTTP 请求 → 协议适配 → ReActAgent.streamEvents()（Flux<AgentEvent>）→ 事件转换 → SSE data 帧`。wiki 中 AG-UI 的时序图明确画出该链路：`Adapter→Agent: streamEvents(msgs, options, ctx)`，`Agent-->>Adapter: Flux<AgentEvent>`（来源：`.qoder/repowiki/zh/content/示例与教程/实战案例/AGUI集成示例.md:96-103`）。

### 1.1 核心底层 API：`streamEvents()`

`ReActAgent` 提供多个重载，返回 `reactor.core.publisher.Flux<AgentEvent>`（来源：`agentscope-core/src/main/java/io/agentscope/core/ReActAgent.java:1086-1133`）：

```java
public Flux<AgentEvent> streamEvents(List<Msg> msgs) { ... }          // :1086
public Flux<AgentEvent> streamEvents(Msg msg) { ... }                 // :1096
public Flux<AgentEvent> streamEvents(List<Msg> msgs, RuntimeContext context) { ... } // :1111
public Flux<AgentEvent> streamEvents(Msg msg, RuntimeContext context) { ... }         // :1123
public Flux<AgentEvent> streamEvents(String text) { ... }             // :1133
```

其 Javadoc 指出 `call()` 与 `streamEvents()` 共享同一个内部 `buildAgentStream` 核心，流末尾会先发出携带最终 `Msg` 的 `AgentResultEvent`，再发 `AgentEndEvent`；并发调用不共享状态（来源：`ReActAgent.java:1075-1085`）。

框架内部事件类型（`AgentEvent`，JSON 判别字段为 `type`）：`AGENT_START / AGENT_END / AGENT_RESULT / MODEL_CALL_START / MODEL_CALL_END / TEXT_BLOCK_START / TEXT_BLOCK_DELTA / TEXT_BLOCK_END / THINKING_BLOCK_* / DATA_BLOCK_* / TOOL_CALL_START / TOOL_CALL_DELTA / TOOL_CALL_END / TOOL_RESULT_* / EXCEED_MAX_ITERS / REQUIRE_USER_CONFIRM / CUSTOM` 等（来源：`agentscope-core/src/main/java/io/agentscope/core/event/AgentEvent.java:36-63`）。

单轮无工具响应的标准事件序列（来源：`agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/streaming/AgentEventStreamExample.java:43-62`）：

```
AGENT_START
  MODEL_CALL_START
    TEXT_BLOCK_START
      TEXT_BLOCK_DELTA  (repeated — one per streamed token chunk)
    TEXT_BLOCK_END
  MODEL_CALL_END        (carries token usage)
AGENT_END
```

`TextBlockDeltaEvent` 携带三个业务字段：`replyId`、`blockId`、`delta`（增量文本）（来源：`agentscope-core/src/main/java/io/agentscope/core/event/TextBlockDeltaEvent.java:20-26`）。

---

## 2. 方案 A：自研 Spring SSE 端点（最小可运行示例）

**最小可运行示例文件**：`agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/streaming/StreamingWebExample.java`（Spring Boot 应用，端口 8080）。

### 2.1 端点与请求格式

```java
@GetMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chat(
        @RequestParam String message,
        @RequestParam(defaultValue = "default") String sessionId) {
```
（来源：`StreamingWebExample.java:108-111`）

- **握手**：`GET http://localhost:8080/chat?message=Hello&sessionId=my-session`，响应 `Content-Type: text/event-stream`；`sessionId` 缺省 `"default"`。
- **调用方式**（README 同款）：`curl -N "http://localhost:8080/chat?message=What%20is%20AI?&sessionId=my-session"`（来源：`agentscope-examples/README.md:274-283`）。

### 2.2 服务端完整链路

```java
AgentStateStore stateStore = new JsonFileAgentStateStore(sessionPath);

// IMPORTANT: Create a new agent per request. ReActAgent is NOT thread-safe — a single
// instance rejects concurrent call()s. Model and AgentStateStore are safe to share;
// Toolkit is deep-copied inside build(). This is the recommended pattern for web apps.
ReActAgent agent =
        ReActAgent.builder()
                .name("WebAgent")
                .model(DashScopeChatModel.builder()
                        .apiKey(apiKey).modelName("qwen-plus").stream(true)   // 模型层开启流式
                        .build())
                .stateStore(stateStore)          // 会话持久化
                .defaultSessionId(sessionId)     // 多轮对话的会话 ID
                .build();

Msg userMsg = new UserMessage(message);

return agent.streamEvents(userMsg)
        .subscribeOn(Schedulers.boundedElastic())
        .filter(event -> event instanceof TextBlockDeltaEvent)
        .map(event -> ((TextBlockDeltaEvent) event).getDelta());
```
（来源：`StreamingWebExample.java:113-136`）

要点：
- **每请求新建 agent 实例**（ReActAgent 非线程安全；Model 与 StateStore 可共享），这是官方注释明确给出的 Web 应用推荐模式（来源：`StreamingWebExample.java:115-117`）。
- 模型侧需 `.stream(true)` 才会有逐 token 的 `TEXT_BLOCK_DELTA`。
- 返回 `Flux<String>` 时 Spring 自动按 SSE 帧序列化，线上格式即 `data:你`、`data:好` 这样的裸文本 delta，**无事件名、无 JSON 包装、无心跳**；Flux complete 后连接关闭。

### 2.3 会话管理（多轮对话）

用 `JsonFileAgentStateStore`（落盘 `~/.agentscope/examples/web-sessions/`）+ builder 上的 `.stateStore(...).defaultSessionId(sessionId)`：同一 `sessionId` 再次请求时自动恢复历史记忆（来源：`StreamingWebExample.java:84-89、113、127-128`；迁移注释见 `:40-45`）。

---

## 3. 方案 B：AG-UI 协议 Starter（官方主推）

**模块**：协议实现 `agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui`；Spring Boot Starter `agentscope-extensions/agentscope-spring-boot-starters/agentscope-agui-spring-boot-starter`（MVC 与 WebFlux 双栈自动装配）。

**可运行示例**：`agentscope-examples/agui/`（`AguiExampleApplication`，端口 8080，前端静态页 `http://localhost:8080`）。

### 3.1 端点、握手与请求体

MVC 栈的 REST 控制器（WebFlux 栈行为一致）：

```java
@PostMapping(
        value = "${agentscope.agui.path-prefix:/agui}/run",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter run(@RequestBody String body,
        @RequestHeader(value = "${agentscope.agui.agent-id-header:X-Agent-Id}", required = false)
                String agentIdHeader,
        HttpServletRequest request) { ... }

@PostMapping(value = "${agentscope.agui.path-prefix:/agui}/run/{agentId}", ...)
public SseEmitter runWithAgentId(@PathVariable String agentId, ...) { ... }
```
（来源：`agentscope-spring-boot-starters/agentscope-agui-spring-boot-starter/src/main/java/io/agentscope/spring/boot/agui/mvc/AguiRestController.java:78-117`）

- **URL**：`POST /agui/run`（前缀 `agentscope.agui.path-prefix` 可配）；启用路径路由时 `POST /agui/run/{agentId}`。
- **Headers**：`Content-Type: application/json`；`Accept: text/event-stream`；可选 `X-Agent-Id`（头名可配）。
- **Agent ID 解析优先级**：URL 路径变量 > `X-Agent-Id` 头 > 请求体 `forwardedProps.agentId` > 配置 `default-agent-id` > `"default"`（来源：`AguiRestController.java:63-72`、`AguiMvcController.java:51-58`）。

**请求体（RunAgentInput）**（来源：`agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/model/RunAgentInput.java:54-104`）：

```json
{
  "threadId": "thread-123",        // 会话线程 ID（必填）
  "runId": "run-456",              // 本次运行 ID（必填）
  "messages": [                    // 对话消息（必填数组，可为空）
    { "id": "m1", "role": "user", "content": "Hello!" }
  ],
  "tools": [],                     // 前端注入的工具 schema（可选）
  "context": [],                   // 附加上下文（可选）
  "state": {},                     // 共享状态（可选）
  "forwardedProps": {},            // 透传属性，可带 agentId（可选）
  "resume": []                     // 中断恢复应答（可选）
}
```

`AguiMessage` 字段：`id`、`role`（user/assistant/system/tool）、`content`（纯文本或结构化 blocks）、`toolCalls`、`toolCallId`（来源：`agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/model/AguiMessage.java:42-74`）。

**curl 示例**（来源：`agentscope-examples/agui/src/main/java/io/agentscope/examples/agui/AguiExampleApplication.java:31-52`）：

```bash
curl -N -X POST http://localhost:8080/agui/run \
  -H "Content-Type: application/json" \
  -d '{"threadId":"test","runId":"1","messages":[{"id":"m1","role":"user","content":"Hello!"}]}'
```

### 3.2 事件类型与数据格式（SSE 帧）

每个 SSE 帧为 `data: {JSON}\n\n`，JSON 由 `AguiEventEncoder.encodeToJson(event)` 序列化，判别字段为 `type`（来源：`AguiEventEncoder.java:73`；MVC 发送见 `AguiMvcController.java:304-311`：`emitter.send(SseEmitter.event().data(jsonData, MediaType.APPLICATION_JSON))`；WebFlux 发送见 `AguiWebFluxHandler.java:160-167、185`）。

完整事件类型清单（Jackson 子类型注册表，来源：`agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java:43-82`）：

| 分类 | type 值 | 关键字段 |
|---|---|---|
| 运行生命周期 | `RUN_STARTED` | threadId, runId, parentRunId?, input? |
| | `RUN_FINISHED` | threadId, runId, result?, outcome（success / interrupt）|
| | `RUN_ERROR` | threadId, runId, message, code? |
| 文本消息 | `TEXT_MESSAGE_START` | messageId, role |
| | `TEXT_MESSAGE_CONTENT` | messageId, **delta**（增量文本）|
| | `TEXT_MESSAGE_END` | messageId |
| 推理/思考 | `REASONING_START` / `REASONING_MESSAGE_START` / `REASONING_MESSAGE_CONTENT`（delta）/ `REASONING_MESSAGE_END` / `REASONING_END` / `REASONING_MESSAGE_CHUNK` / `REASONING_ENCRYPTED_VALUE` | messageId, delta 等 |
| 工具调用 | `TOOL_CALL_START` | toolCallId, toolCallName |
| | `TOOL_CALL_ARGS` | toolCallId, delta（参数 JSON 片段）|
| | `TOOL_CALL_END` / `TOOL_CALL_RESULT` | toolCallId; content, role?, messageId? |
| 状态同步 | `STATE_SNAPSHOT` | snapshot（整体替换）|
| | `STATE_DELTA` | delta（RFC 6902 JSON Patch 操作列表）|
| 消息快照 | `MESSAGES_SNAPSHOT` | messages[]（RUN_FINISHED 后同步全量消息）|
| 活动面板 | `ACTIVITY_SNAPSHOT` / `ACTIVITY_DELTA` | messageId, activityType, content/patch |
| 步骤 | `STEP_STARTED` / `STEP_FINISHED` | stepName |
| 透传 | `RAW` / `CUSTOM` | event/source 或 name/value |
| 兼容块 | `TEXT_MESSAGE_CHUNK` / `TOOL_CALL_CHUNK` | 自动开合消息的便捷事件 |

（各 record 字段定义来源：`AguiEvent.java:155-1626`；`TextMessageContent` 的 delta 字段见 `:318-342`，`StateDelta` 的 JSON Patch 见 `:680-722`，`RunError` 见 `:1130-1154`）

一次典型对话的 SSE 帧序列：

```
data: {"type":"RUN_STARTED","threadId":"t1","runId":"r1"}

data: {"type":"TEXT_MESSAGE_START","threadId":"t1","runId":"r1","messageId":"m-1","role":"assistant"}

data: {"type":"TEXT_MESSAGE_CONTENT","threadId":"t1","runId":"r1","messageId":"m-1","delta":"你"}

data: {"type":"TEXT_MESSAGE_CONTENT","threadId":"t1","runId":"r1","messageId":"m-1","delta":"好"}

data: {"type":"TEXT_MESSAGE_END","threadId":"t1","runId":"r1","messageId":"m-1"}

data: {"type":"RUN_FINISHED","threadId":"t1","runId":"r1"}
```

**结束与错误标记**：正常结束以 `RUN_FINISHED` 收尾；出错时 MVC 控制器先发 `Raw{error:...}` 再补 `RUN_FINISHED` 后关闭（来源：`AguiMvcController.java:313-331`；请求体解析失败同样返回该组合的 HTTP 400 SSE 体，`AguiRestController.java:125-141`）。新版协议已引入 `RUN_ERROR` 事件替代旧的 Raw+RunFinished 模式（来源：`AguiEvent.java:1125-1129` Javadoc）。

**心跳**：两套实现（MVC/WebFlux）均**未发现**ping/keep-alive 心跳；靠 `sseTimeout`（默认 600000ms = 10 分钟）兜底（来源：`AguiMvcController.java:78-101` 中 `sseTimeout` 默认值；全文无 heartbeat 相关代码）。

**断连行为**：`interruptOnDisconnect=true`（默认）时，客户端断开会触发 `doOnCancel` → 中断 agent 运行；WebFlux 侧实现见 `AguiWebFluxHandler.java:168-183`，MVC 侧 `onError/onTimeout` 回调见 `AguiMvcController.java:183-216`。

### 3.3 Agent 注册与配置

通过声明 `AguiAgentRegistryCustomizer` Bean 注册 Agent 工厂（每请求新实例）：

```java
@Bean
public AguiAgentRegistryCustomizer aguiAgentRegistryCustomizer() {
    return registry -> {
        registry.registerFactory("default", this::createDefaultAgent);
        registry.registerFactory("chat", this::createChatAgent);
        registry.registerFactory("calculator", this::createCalculatorAgent);
    };
}
```
（来源：`agentscope-examples/agui/src/main/java/io/agentscope/examples/agui/config/AgentConfiguration.java:58-81`；Agent 构建示例 `:128-154`，模型需 `.stream(true)`）

`application.yml` 关键配置（来源：`agentscope-examples/agui/src/main/resources/application.yml:30-54`）：

```yaml
agentscope:
  agui:
    path-prefix: /agui
    cors-enabled: true
    cors-allowed-origins: ["*"]
    run-timeout: 10m
    emit-state-events: true      # 输出 STATE_* 事件
    emit-tool-call-args: true    # 输出 TOOL_CALL_ARGS 增量参数
    emit-token-usage: true
    default-agent-id: default
    agent-id-header: X-Agent-Id
    enable-path-routing: true    # POST /agui/run/{agentId}
    server-side-memory: true     # 服务端会话记忆（关键，见 3.5）
    max-thread-sessions: 1000
    session-timeout-minutes: 30
    enable-reasoning: true       # 输出 REASONING_* 事件
```

依赖坐标（示例 pom）：`io.agentscope:agentscope-extensions-agui` + `io.agentscope:agentscope-agui-spring-boot-starter`（来源：`agentscope-examples/agui/pom.xml:61-65`）。

### 3.4 服务端处理流程（MVC 实现）

`AguiMvcController.handleInternal`：创建 `SseEmitter(sseTimeout)` → 提交线程池 → `processor.process(input, ...)` 得到 `result.events()`（`Flux<AguiEvent>`）→ 逐事件 `encoder.encodeToJson` 后 `emitter.send` → complete/onError（来源：`agentscope-spring-boot-starters/agentscope-agui-spring-boot-starter/src/main/java/io/agentscope/spring/boot/agui/mvc/AguiMvcController.java:161-253`）。WebFlux 版直接把 `Flux<AguiEvent>` map 成 `ServerSentEvent<String>` 流返回（来源：`AguiWebFluxHandler.java:141-190`）。

### 3.5 会话（thread）管理：多轮对话与历史

两种模式（来源：`.qoder/repowiki/zh/content/示例与教程/实战案例/AGUI集成示例.md:126-145、218-227`）：

- **服务端记忆（推荐）**：`server-side-memory: true` 时由 `ThreadSessionManager` 按 `threadId` 缓存 Agent 实例与记忆，**每次请求只传最新一条用户消息**即可，历史由服务端记忆维护；`max-thread-sessions`/`session-timeout-minutes` 控制上限与过期（配置来源：`application.yml:51-53`；解析逻辑见 `AguiRequestProcessor`，wiki 引 `AguiRequestProcessor.java:121-207`）。
- **客户端记忆**：`server-side-memory: false` 时每次请求携带完整 `messages` 历史（AG-UI 标准模式，CopilotKit 等前端框架默认行为）。

---

## 4. 方案 C：Chat Completions Web Starter（OpenAI 兼容）

**模块**：`agentscope-extensions-agui` 平级的 `agentscope-extensions-chat-completions-web`（协议）+ `agentscope-chat-completions-web-starter`（Spring 装配）。

- **端点**：`POST ${agentscope.chat-completions.base-path:/v1/chat/completions}`，`consumes: application/json`；非流式与流式共用同一路径——请求体 `stream=true` 时即使没带 `Accept: text/event-stream` 也自动切换 SSE（来源：`agentscope-spring-boot-starters/agentscope-chat-completions-web-starter/src/main/java/io/agentscope/spring/boot/chat/web/ChatCompletionsController.java:123-144`；流式专用映射 `produces=TEXT_EVENT_STREAM_VALUE` 见 `:213-217`）。
- **请求体**：标准 OpenAI 格式 `{ model, messages, stream, tools? }`；**无状态**——客户端必须每次传完整消息历史（来源：`ChatCompletionsController.java:166-190`，每请求 `agentProvider.getObject()` 新建 agent；wiki 兼容性说明见 `.qoder/repowiki/zh/content/扩展生态系统/协议适配器/Chat Completions Web适配器.md:348-361`）。
- **响应帧**：`data: {ChatCompletionsChunk JSON}`（`object: "chat.completion.chunk"`，增量在 `choices[].delta`），**流末尾追加 `data: [DONE]` 结束标记**（来源：`agentscope-spring-boot-starters/agentscope-chat-completions-web-starter/src/main/java/io/agentscope/spring/boot/chat/service/ChatCompletionsStreamingService.java:71-84、108-110`）：

```java
public Flux<ServerSentEvent<String>> streamAsSse(...) {
    return streamingAdapter.stream(agent, messages, requestId, model)
            .map(this::chunkToSseEvent)
            .concatWith(Flux.just(createDoneSseEvent()));   // data: [DONE]
}
```

错误时会发出 error chunk 事件而非直接断流（来源：`ChatCompletionsStreamingService.java:112-126`、`ChatCompletionsController.java:283-291`）。

---

## 5. 方案 D：Agent Protocol 扩展（内部任务协议 + 断线续传）

`AgentProtocolController` 暴露内部任务协议（先建任务、再订阅事件流）：

```java
@PostMapping(value = "/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)   // body: task_id, agent_id, input, context
public ResponseEntity<Map<String, Object>> submit(...) { ... }               // 返回 {"task_id":..,"status":"pending"}

/** SSE event stream for a task. Supports from_seq query param and Last-Event-ID header for reconnect. */
@GetMapping(value = "/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> events(@PathVariable String taskId,
        @RequestParam(name = "from_seq", required = false) Long fromSeq,
        @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) { ... }
```
（来源：`agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agent-protocol/src/main/java/io/agentscope/extensions/agentprotocol/AgentProtocolController.java:59-77、104-126`）

特点：异步任务模型（另有 `GET /tasks/{id}`、`GET /tasks/{id}/wait`、`POST /tasks/{id}/cancel`、`POST /tasks/{id}/resume`）；SSE 订阅支持 `from_seq` 查询参数与标准 `Last-Event-ID` 请求头做**断线续传**，流带超时 `take(timeout)`（来源：`AgentProtocolController.java:83-126`）。适合服务间集成，不适合浏览器直接对话。

---

## 6. 客户端消费示例

### 6.1 前端（原生 fetch + ReadableStream）——官方 `agui-client.js`

**文件**：`agentscope-examples/agui/src/main/resources/static/js/agui-client.js`（零依赖纯 JS）。

发起请求（POST + Accept + AbortController）：

```javascript
async run(input, callbacks = {}) {
    this.abortController = new AbortController();
    const response = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
        body: JSON.stringify(input),
        signal: this.abortController.signal      // abort() 可中断 → 服务端触发 agent.interrupt
    });
    const reader = response.body.getReader();
    ...
}
```
（来源：`agui-client.js:81-101`；abort 说明 `:46-54`）

解析 SSE：以 `\n\n`（兼容 `\r\n\r\n`）切帧，逐行取 `data:` 前缀后 `JSON.parse`，按 `event.type` 分发（来源：`agui-client.js:121-163、194-282`）：

```javascript
switch (type) {
    case 'RUN_STARTED':      callbacks.onRunStarted?.(event.threadId, event.runId); break;
    case 'TEXT_MESSAGE_CONTENT': callbacks.onTextContent?.(event.delta, event.messageId); break;
    case 'REASONING_MESSAGE_CONTENT': callbacks.onReasoningContent?.(event.delta, event.messageId); break;
    case 'TOOL_CALL_START':  callbacks.onToolCallStart?.(event.toolCallId, event.toolCallName); break;
    case 'RUN_FINISHED':     callbacks.onRunFinished?.(event.threadId, event.runId, event); break;
    ...
}
```

页面侧用法（来源：`agentscope-examples/agui/src/main/resources/static/index.html:362-363`）：

```javascript
const client = new AguiClient('/agui/run');
let threadId = 'thread-' + Date.now();     // 同一 threadId + server-side-memory 即多轮对话
```

注意：AG-UI 端点是 POST，**不能用浏览器原生 `EventSource`**（其只支持 GET），必须用 fetch 流式读取。

### 6.2 前端（CopilotKit v2 React SDK）

**示例**：`agentscope-examples/agentscope-copilotkit/`（后端复用 AG-UI Starter）。

后端路由（来源：`agentscope-examples/agentscope-copilotkit/src/main/java/io/agentscope/examples/copilotkit/config/CopilotKitRouteConfiguration.java:49-112`）：`GET /agui/run/info`（Runtime 信息）、`/agui/run/threads*`（线程管理）、`POST /agui/run/agent/{agentId}/connect`（SSE 连接，`TEXT_EVENT_STREAM` + `ServerSentEvent`）、`POST /agui/run/agent/{agentId}/run`（**直接复用 starter 的 `aguiHandler::handleWithAgentId`**，`:108`）、`POST .../stop/{threadId}`（中断）。

前端编程式触发（来源：`agentscope-examples/agentscope-copilotkit/frontend/src/hooks/useCapabilityRunner.ts:19-33`）：

```typescript
const { copilotkit } = useCopilotKit();
const { agent } = useAgent({ agentId: CHAT_AGENT_ID });   // "workbench"
agent.addMessage({ id: crypto.randomUUID(), role: "user", content: prompt });
await copilotkit.runAgent({ agent });                     // SDK 内部走 AG-UI SSE
```

SSE 解析、事件到 UI 的映射（`useCopilotChat`、`useRenderTool`、`useInterrupt` 等）全部由 `@copilotkit/react-core/v2` 承担；多路由模式 `useSingleEndpoint={false}`（来源：`agentscope-examples/agentscope-copilotkit/README.md:15-31`）。该示例还演示了 `STATE_DELTA` 驱动任务看板、`ACTIVITY_SNAPSHOT` 生成式 UI、AG-UI interrupt 驱动 HITL（来源：`frontend/src/copilot/constants.ts:23-56`）。

### 6.3 Java 客户端（WebClient/Flux，依据协议格式编写）

框架自带示例均为服务端，未提供 Java HTTP 消费端示例；以下为按上述协议编写的消费写法（协议依据：`AguiRestController.java:78-91` 端点契约 + `AguiEvent.java:43-82` 事件 JSON）。若同为 Spring 5+/WebFlux 环境：

```java
// 服务间调用：消费 AG-UI SSE 流（自写示例，协议格式见 AguiEvent.java:43-82）
WebClient client = WebClient.builder().baseUrl("http://agent-host:8080").build();

String body = """
    {"threadId":"t-1","runId":"r-1",
     "messages":[{"id":"m1","role":"user","content":"你好"}]}
    """;

Flux<String> deltas = client.post().uri("/agui/run")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.TEXT_EVENT_STREAM)
        .bodyValue(body)
        .retrieve()                                  // 2xx 校验
        .bodyToFlux(String.class)                    // SSE data 帧的 JSON 字符串
        .takeUntil(json -> json.contains("\"RUN_FINISHED\"")
                       || json.contains("\"RUN_ERROR\""))   // 结束标记
        .filter(json -> json.contains("\"TEXT_MESSAGE_CONTENT\""))
        .map(json -> extractDelta(json));            // 取 "delta" 字段拼装

deltas.doOnNext(System.out::print).blockLast();
```

更类型化的做法：把 `agentscope-extensions-agui` 作为客户端依赖引入，直接反序列化为 `AguiEvent` 密封接口（Jackson 多态注册见 `AguiEvent.java:43-44`）：

```java
.bodyToFlux(AguiEvent.class)          // Jackson 按 "type" 反序列化到对应 record
 .ofType(AguiEvent.TextMessageContent.class)
 .map(AguiEvent.TextMessageContent::delta)
```

若是简单文本场景（方案 A 的 `Flux<String>` 端点），`bodyToFlux(String.class)` 每个元素即一个 delta 文本片段，无需 JSON 解析。

---

## 7. 方案对比与推荐

| 维度 | A. 自研 SSE | B. AG-UI Starter | C. Chat Completions | D. Agent Protocol |
|---|---|---|---|---|
| 端点 | 自定义 GET | `POST /agui/run` | `POST /v1/chat/completions` | `POST /tasks` + `GET .../events` |
| 事件格式 | 裸文本 delta | 结构化 JSON（`type` 判别）| OpenAI chunk + `[DONE]` | 内部任务事件 + `id`/`Last-Event-ID` |
| 富事件（工具/思考/状态）| 无（需自己 map）| 全（TEXT/REASONING/TOOL/STATE/ACTIVITY...）| 文本 + 工具调用 chunk | 任务生命周期 |
| 会话 | `stateStore` + `sessionId` | `threadId` + 服务端记忆 或 客户端全量 messages | 无状态（客户端全量）| `task_id` |
| 中断/断连 | 客户端断流即取消订阅 | `interruptOnDisconnect` → `agent.interrupt` | 无内建 | `cancel` + 断线续传 |
| 前端生态 | 自己写 | AG-UI / CopilotKit 生态 | 任意 OpenAI SDK/客户端 | 内部系统 |
| 集成成本 | 最低 | 引 starter + 注册工厂 | 引 starter | 引扩展 |

**推荐（面向 NexAI 集成 AgentScope Java）**：

1. **对外聊天/Agent 交互首选方案 B（AG-UI Starter）**：一条依赖即可获得完整 SSE 协议（流式文本、思考流、工具调用可视化、状态同步、HITL interrupt），且前端可无缝接 CopilotKit 或直接复用 `agui-client.js`；多轮对话用 `threadId` + `server-side-memory`。NexAI 的 `nexai-server` 是 Spring MVC 栈，Starter 的 MVC 自动装配（`SseEmitter`）可直接叠加在现有应用上；若担心与芋道现有 Web 配置冲突，WebFlux 路由模式二选一即可。
2. **Java 服务间内部调用或极简场景用方案 A**：在 NexAI 的 DDD 模块 `interfaces/controller/admin` 下自建 `Flux<ServerSentEvent>` 端点，`agent.streamEvents()` + 按需过滤 `TextBlockDeltaEvent`，出参直接用 application 层 dto；注意**每请求新建 agent 实例**（官方明确 ReActAgent 非线程安全）与模型 `.stream(true)`。
3. **若需要让既有 OpenAI 客户端/SDK 直接调用**（如 Dify、LangChain、openai-python），叠加方案 C，`stream=true` 即得 SSE + `[DONE]`。
4. 方案 D 面向 AgentScope 内部多平面架构，NexAI 暂无必要。

通用注意事项：三条主要通道均**无 SSE 心跳**，经 Nginx/网关代理时需关闭响应缓冲（`proxy_buffering off`、`X-Accel-Buffering: no`）并保证读超时 > 10 分钟（AG-UI 默认 `sseTimeout=600000ms`，来源：`AguiMvcController.java:101`）。

---

## 附录：关键文件索引

| 文件 | 作用 |
|---|---|
| `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/streaming/StreamingWebExample.java` | 方案 A 最小可运行示例（Spring Boot + SSE）|
| `agentscope-examples/documentation/src/main/java/io/agentscope/examples/documentation2/streaming/AgentEventStreamExample.java` | `streamEvents()` 全事件序列演示 |
| `agentscope-examples/agui/src/main/java/io/agentscope/examples/agui/AguiExampleApplication.java` | AG-UI 示例入口（curl 用法）|
| `agentscope-examples/agui/src/main/java/io/agentscope/examples/agui/config/AgentConfiguration.java` | Agent 注册（AguiAgentRegistryCustomizer）|
| `agentscope-examples/agui/src/main/resources/application.yml` | AG-UI 全量配置 |
| `agentscope-examples/agui/src/main/resources/static/js/agui-client.js` | 前端 SSE 消费参考实现 |
| `agentscope-extensions/agentscope-spring-boot-starters/agentscope-agui-spring-boot-starter/src/main/java/io/agentscope/spring/boot/agui/mvc/AguiRestController.java` | AG-UI MVC 端点定义 |
| `.../mvc/AguiMvcController.java` | AG-UI MVC SseEmitter 处理流程 |
| `.../webflux/AguiWebFluxHandler.java` | AG-UI WebFlux ServerSentEvent 处理流程 |
| `agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agui/src/main/java/io/agentscope/core/agui/event/AguiEvent.java` | AG-UI 事件类型与字段权威定义 |
| `.../model/RunAgentInput.java`、`.../model/AguiMessage.java` | AG-UI 请求体模型 |
| `agentscope-extensions/agentscope-spring-boot-starters/agentscope-chat-completions-web-starter/src/main/java/io/agentscope/spring/boot/chat/web/ChatCompletionsController.java` | OpenAI 兼容端点 |
| `.../service/ChatCompletionsStreamingService.java` | chunk + `[DONE]` 序列化 |
| `agentscope-extensions/agentscope-extensions-protocol/agentscope-extensions-agent-protocol/src/main/java/io/agentscope/extensions/agentprotocol/AgentProtocolController.java` | 任务协议 SSE + 断线续传 |
| `agentscope-examples/agentscope-copilotkit/` | CopilotKit v2 端到端示例（后端复用 AG-UI）|
| `.qoder/repowiki/zh/content/示例与教程/实战案例/AGUI集成示例.md` | AG-UI 集成中文 wiki（架构图/配置项）|
| `.qoder/repowiki/zh/content/扩展生态系统/协议适配器/Chat Completions Web适配器.md` | Chat Completions 中文 wiki |
