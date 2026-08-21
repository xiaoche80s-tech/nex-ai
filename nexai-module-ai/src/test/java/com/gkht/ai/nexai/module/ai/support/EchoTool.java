package com.gkht.ai.nexai.module.ai.support;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * S1 测试用调试工具：注册进运行时 Toolkit，配合 {@link FakeChatModel#callTool}
 * 验证工具调用事件流（TOOL_CALL_* / TOOL_RESULT_*）与工具结果回填。
 */
public class EchoTool {

    @Tool(name = "echo", description = "原样返回输入文本，用于验证工具调用链路")
    public String echo(@ToolParam(name = "text", description = "要回显的文本") String text) {
        return "echo:" + text;
    }

}
