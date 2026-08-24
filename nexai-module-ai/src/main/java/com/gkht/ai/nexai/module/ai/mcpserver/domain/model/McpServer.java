package com.gkht.ai.nexai.module.ai.mcpserver.domain.model;

import com.gkht.ai.nexai.module.ai.mcpserver.domain.exception.McpToolsWhitelistInvalidException;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpOwnerType;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpTransport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP Server 聚合根（充血模型，零框架依赖）：租户注册的外部工具服务接入点（BYO-MCP）——
 * 传输（stdio / SSE / Streamable HTTP）+ 端点/命令 + 认证头 + 工具白名单。
 * 双归属字段与 Channel 同构（MVP 固定 TENANT，PLATFORM 预留）。
 *
 * <p>白名单语义：allowedTools 为 server 级放行面（空 = 该 server 全部工具），
 * 运行时挂载（ToolMount）可在此基础上再收敛；availableTools 为最近一次连通探测拉取到的
 * 工具名清单（信息性缓存，探测成功时自动更新），用于白名单配置的拼写校验与挂载编辑面的候选项。</p>
 *
 * <p>认证头仅在 HTTP 系传输（SSE / Streamable HTTP）下有意义，stdio 凭证走 env 环境变量。</p>
 */
public class McpServer {

    /** 名称长度上限（字符） */
    static final int NAME_MAX_LENGTH = 64;
    /** 端点地址长度上限（字符） */
    static final int ENDPOINT_MAX_LENGTH = 512;
    /** 命令长度上限（字符） */
    static final int COMMAND_MAX_LENGTH = 255;
    /** 白名单数量上限 */
    static final int ALLOWED_TOOLS_MAX_SIZE = 128;
    /** 探测工具清单数量上限（防御性：异常 server 返回超长清单时截断报错） */
    static final int AVAILABLE_TOOLS_MAX_SIZE = 1024;

    /** 编号，未落库时为 null */
    private Long id;
    /** 名称（管理标识，同一租户内用于区分多个 MCP Server） */
    private String name;
    /** 传输类型 */
    private McpTransport transport;
    /** 端点地址（SSE / Streamable HTTP 必填，http/https），stdio 传输须为 null */
    private String endpoint;
    /** 启动命令（stdio 必填），HTTP 系传输须为 null */
    private String command;
    /** stdio 启动参数 */
    private List<String> args;
    /** stdio 环境变量（凭证走此处） */
    private Map<String, String> env;
    /** 认证头（HTTP 系传输，如 Authorization），stdio 传输须为空 */
    private Map<String, String> headers;
    /** 请求超时（秒），null = 运行时默认 */
    private Integer timeoutSeconds;
    /** 工具白名单（server 级放行面），空 = 全部工具 */
    private List<String> allowedTools;
    /** 最近一次探测拉取的工具名清单（信息性缓存） */
    private List<String> availableTools;
    /** 是否启用 */
    private boolean enabled;
    /** 归属维度，MVP 固定租户侧（BYO-MCP） */
    private McpOwnerType ownerType;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;
    /** 更新时间，由持久化填充；参与常驻实例版本戳（MCP 配置变更即失效重建） */
    private LocalDateTime updateTime;

    private McpServer(Long id, String name, McpTransport transport, String endpoint, String command,
                      List<String> args, Map<String, String> env, Map<String, String> headers,
                      Integer timeoutSeconds, List<String> allowedTools, List<String> availableTools,
                      boolean enabled, McpOwnerType ownerType, LocalDateTime createTime,
                      LocalDateTime updateTime) {
        this.id = id;
        this.name = name;
        this.transport = transport;
        this.endpoint = endpoint;
        this.command = command;
        this.args = args;
        this.env = env;
        this.headers = headers;
        this.timeoutSeconds = timeoutSeconds;
        this.allowedTools = allowedTools;
        this.availableTools = availableTools;
        this.enabled = enabled;
        this.ownerType = ownerType;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 注册 MCP Server，初始为启用状态
     *
     * @param name           名称，不能为空白
     * @param transport      传输类型，不能为 null
     * @param endpoint       端点地址（HTTP 系传输必填），可空
     * @param command        启动命令（stdio 必填），可空
     * @param args           stdio 启动参数，可空
     * @param env            stdio 环境变量，可空
     * @param headers        认证头（仅 HTTP 系传输），可空
     * @param timeoutSeconds 请求超时（秒），可空 = 运行时默认
     * @param allowedTools   工具白名单，可空 = 全部工具
     * @param ownerType      归属维度，不能为 null
     */
    public static McpServer create(String name, McpTransport transport, String endpoint,
                                   String command, List<String> args, Map<String, String> env,
                                   Map<String, String> headers, Integer timeoutSeconds,
                                   List<String> allowedTools, McpOwnerType ownerType) {
        if (ownerType == null) {
            throw new IllegalArgumentException("MCP Server 归属维度不能为空");
        }
        validateBasics(name, transport, endpoint, command, headers, timeoutSeconds, allowedTools);
        return new McpServer(null, name.strip(), transport, normalizeNullable(endpoint),
                normalizeNullable(command), snapshotList(args), copyMap(env), copyMap(headers),
                timeoutSeconds, snapshotList(allowedTools), List.of(), true, ownerType, null, null);
    }

    /**
     * 从持久化数据重建聚合（Repository 专用，字段原样恢复）
     */
    public static McpServer reconstitute(Long id, String name, McpTransport transport,
                                         String endpoint, String command, List<String> args,
                                         Map<String, String> env, Map<String, String> headers,
                                         Integer timeoutSeconds, List<String> allowedTools,
                                         List<String> availableTools, boolean enabled,
                                         McpOwnerType ownerType, LocalDateTime createTime,
                                         LocalDateTime updateTime) {
        return new McpServer(id, name, transport, endpoint, command, snapshotList(args),
                copyMap(env), copyMap(headers), timeoutSeconds, snapshotList(allowedTools),
                snapshotList(availableTools), enabled, ownerType, createTime, updateTime);
    }

    /**
     * 更新接入配置。headers 传 null 表示保留原认证头（编辑界面不回传凭证，未修改则不覆盖；
     * 凭证只能保留或更换，不存在清除——无认证的 server 重建即可）。
     */
    public void update(String name, McpTransport transport, String endpoint, String command,
                       List<String> args, Map<String, String> env, Map<String, String> headers,
                       Integer timeoutSeconds, List<String> allowedTools) {
        validateBasics(name, transport, endpoint, command, headers, timeoutSeconds, allowedTools);
        // 白名单变更校验：已有探测清单时，白名单必须是清单子集（防拼写错把工具悄悄放空）
        if (!availableTools.isEmpty() && !allowedTools.isEmpty()
                && !availableTools.containsAll(allowedTools)) {
            throw new McpToolsWhitelistInvalidException(
                    "工具白名单中的工具不在该 Server 最近拉取的工具清单内");
        }
        this.name = name.strip();
        this.transport = transport;
        this.endpoint = normalizeNullable(endpoint);
        this.command = normalizeNullable(command);
        this.args = snapshotList(args);
        this.env = copyMap(env);
        if (headers != null) {
            this.headers = copyMap(headers);
        }
        this.timeoutSeconds = timeoutSeconds;
        this.allowedTools = snapshotList(allowedTools);
    }

    /** 启用 */
    public void enable() {
        this.enabled = true;
    }

    /** 落库回填编号（Repository 专用：insert 后把 DB 生成的主键写回聚合根） */
    public void assignId(Long id) {
        this.id = id;
    }

    /** 停用：保留数据与凭证，仅退出来用范围（挂载该 server 的规格装配时显式报错） */
    public void disable() {
        this.enabled = false;
    }

    /**
     * 登记探测结果（探测成功时由应用服务调用）：刷新工具清单缓存
     */
    public void applyProbeResult(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            throw new IllegalArgumentException("探测成功必须携带工具清单");
        }
        if (toolNames.size() > AVAILABLE_TOOLS_MAX_SIZE) {
            throw new IllegalArgumentException("工具清单超过上限 " + AVAILABLE_TOOLS_MAX_SIZE);
        }
        this.availableTools = snapshotList(toolNames);
    }

    /** 创建与更新共用的必填与互斥校验 */
    private static void validateBasics(String name, McpTransport transport, String endpoint,
                                       String command, Map<String, String> headers,
                                       Integer timeoutSeconds, List<String> allowedTools) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MCP Server 名称不能为空");
        }
        if (name.strip().length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("MCP Server 名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        if (transport == null) {
            throw new IllegalArgumentException("MCP Server 必须声明传输类型");
        }
        if (transport == McpTransport.STDIO) {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("stdio 传输必须填写启动命令");
            }
            if (endpoint != null && !endpoint.isBlank()) {
                throw new IllegalArgumentException("stdio 传输不使用端点地址");
            }
            if (headers != null && !headers.isEmpty()) {
                throw new IllegalArgumentException("stdio 传输不支持认证头（凭证请走环境变量）");
            }
        } else {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalArgumentException(transport + " 传输必须填写端点地址");
            }
            String stripped = endpoint.strip();
            if (!stripped.startsWith("http://") && !stripped.startsWith("https://")) {
                throw new IllegalArgumentException("端点地址必须为 http/https URL");
            }
            if (command != null && !command.isBlank()) {
                throw new IllegalArgumentException(transport + " 传输不使用启动命令");
            }
        }
        if (command != null && command.strip().length() > COMMAND_MAX_LENGTH) {
            throw new IllegalArgumentException("启动命令不能超过 " + COMMAND_MAX_LENGTH + " 个字符");
        }
        if (timeoutSeconds != null && timeoutSeconds < 1) {
            throw new IllegalArgumentException("请求超时（秒）不能小于 1");
        }
        if (allowedTools != null && (allowedTools.size() > ALLOWED_TOOLS_MAX_SIZE
                || allowedTools.stream().anyMatch(tool -> tool == null || tool.isBlank()))) {
            throw new IllegalArgumentException(
                    "工具白名单不能含空白项且最多 " + ALLOWED_TOOLS_MAX_SIZE + " 项");
        }
    }

    /** 列表规范化为不可变快照（null 视为空列表） */
    private static List<String> snapshotList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** Map 规范化为不可变快照（null 视为空 Map） */
    private static Map<String, String> copyMap(Map<String, String> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    /** 可空文本规范化：空白归 null，其余去首尾空白 */
    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public McpTransport getTransport() {
        return transport;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArgs() {
        return args;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    /** 认证头（可能含 Bearer token），仅供 Repository 与探测/装配网关使用，禁止直接出参 */
    public Map<String, String> getHeaders() {
        return headers;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /** 工具白名单，空列表 = 该 server 全部工具 */
    public List<String> getAllowedTools() {
        return allowedTools;
    }

    /** 最近探测拉取的工具名清单（信息性缓存） */
    public List<String> getAvailableTools() {
        return availableTools;
    }

    /**
     * 翻译为连接配置（工单 21 候选 6）：聚合自身的连接知识（传输/端点或命令/认证头）
     * 收敛为纯 domain 值对象；SDK 侧组装由 infrastructure 建连工厂消费本配置完成，
     * agentscope 类型不再跨聚合裸奔。
     *
     * @param requestTimeout 请求超时（探测/装配各自的取小结果），null = SDK 默认
     */
    public com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpConnectionConfig
    toConnectionConfig(java.time.Duration requestTimeout) {
        return new com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject.McpConnectionConfig(
                "nexai-mcp-" + id, transport, transport == McpTransport.STDIO ? command : null,
                transport == McpTransport.STDIO ? args : null,
                transport == McpTransport.STDIO ? env : null,
                transport == McpTransport.STDIO ? null : endpoint,
                transport == McpTransport.STDIO ? Map.of() : headers,
                requestTimeout);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public McpOwnerType getOwnerType() {
        return ownerType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /** 更新时间（参与常驻实例版本戳），新建时为 null */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof McpServer other)) {
            return false;
        }
        // 聚合根按身份（编号）判等；未落库的聚合只与自身相等
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
