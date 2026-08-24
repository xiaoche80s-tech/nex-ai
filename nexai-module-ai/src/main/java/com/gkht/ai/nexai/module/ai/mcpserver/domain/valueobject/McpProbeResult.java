package com.gkht.ai.nexai.module.ai.mcpserver.domain.valueobject;

import java.util.List;

/**
 * MCP Server 连通探测结果值对象（不可变）：探测不抛异常——成败与原因一律封装在此
 * （与 Channel 的 ConnectivityResult 同构）。成功时携带拉取到的工具清单。
 */
public final class McpProbeResult {

    private final boolean success;
    private final long elapsedMs;
    private final String message;
    private final List<McpToolSummary> tools;

    private McpProbeResult(boolean success, long elapsedMs, String message,
                           List<McpToolSummary> tools) {
        this.success = success;
        this.elapsedMs = elapsedMs;
        this.message = message;
        this.tools = tools;
    }

    /** 探测成功（携带工具清单） */
    public static McpProbeResult success(long elapsedMs, int toolCount, List<McpToolSummary> tools) {
        return new McpProbeResult(true, elapsedMs,
                String.format("连通正常，拉取到 %d 个工具", toolCount), tools);
    }

    /** 探测失败（不可达/认证失败/超时等，根因消息） */
    public static McpProbeResult failure(long elapsedMs, String message) {
        return new McpProbeResult(false, elapsedMs, message, List.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public String getMessage() {
        return message;
    }

    /** 拉取到的工具清单，失败时为空列表 */
    public List<McpToolSummary> getTools() {
        return tools;
    }

}
