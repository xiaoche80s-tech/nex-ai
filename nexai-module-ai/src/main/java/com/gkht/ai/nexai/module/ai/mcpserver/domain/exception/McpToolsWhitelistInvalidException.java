package com.gkht.ai.nexai.module.ai.mcpserver.domain.exception;

/**
 * 工具白名单不在最近拉取的工具清单内（领域异常，携带业务语义）：
 * 白名单配置存在拼写错时由聚合抛出，应用层据此分派专项错误码
 * （不依赖异常消息文本判别，改提示文案不影响错误码路由）。
 */
public class McpToolsWhitelistInvalidException extends IllegalArgumentException {

    public McpToolsWhitelistInvalidException(String message) {
        super(message);
    }

}
