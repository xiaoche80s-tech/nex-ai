package com.gkht.ai.nexai.module.ai.session.domain.exception;

/**
 * 规格要求沙箱但环境无可用 Docker：装配显式失败（不静默降级——规格承诺的能力不可用必须暴露，
 * ADR-0007 决策 7）。由应用层转 SESSION_SANDBOX_UNAVAILABLE 业务错误。
 */
public class SessionSandboxUnavailableException extends RuntimeException {

    public SessionSandboxUnavailableException(String agentName) {
        super("规格要求沙箱但当前环境无可用 Docker：" + agentName);
    }

}
