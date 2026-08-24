package com.gkht.ai.nexai.module.ai.shared.tool.builtin;

import com.gkht.ai.nexai.module.ai.shared.tool.PlatformToolEntry;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 平台工具库示例条目（工单 13：@Tool 注册机制就位并附示例工具）：回显工具组
 * {@code echo}（原样回显）与 {@code echo_upper}（大写回显），用于验证挂载翻译、
 * 白名单收敛与事件流工具调用生命周期；同时作为平台业务工具的编码范式。
 */
@Component
public class SampleEchoTool implements PlatformToolEntry {

    @Override
    public Long getId() {
        return 1L;
    }

    @Override
    public String getCode() {
        return "sample-echo";
    }

    @Override
    public String getName() {
        return "回显示例工具";
    }

    @Override
    public String getDescription() {
        return "平台工具库示例：echo 原样回显输入，echo_upper 大写回显输入（白名单收敛示例用）";
    }

    @Override
    public List<String> getToolNames() {
        return List.of("echo", "echo_upper");
    }

    @Override
    public Object getToolInstance() {
        return new EchoTools();
    }

    /** @Tool POJO：方法名即工具名（snake_case 约定） */
    static class EchoTools {

        @Tool(name = "echo", description = "原样回显输入文本")
        public String echo(
                @ToolParam(name = "text", description = "要回显的文本") String text) {
            return text == null ? "" : text;
        }

        @Tool(name = "echo_upper", description = "大写回显输入文本")
        public String echoUpper(
                @ToolParam(name = "text", description = "要大写回显的文本") String text) {
            return text == null ? "" : text.toUpperCase();
        }

    }

}
