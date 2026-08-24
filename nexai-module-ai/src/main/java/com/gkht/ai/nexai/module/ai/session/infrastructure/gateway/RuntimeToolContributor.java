package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import io.agentscope.core.tool.Toolkit;

/**
 * 运行时工具贡献者扩展点（infrastructure 接缝）：装配常驻实例时向 toolkit 注册工具
 * （@Tool 注解 POJO / ToolBase 实现）。工单 12/13 落地后生产挂载走装配指令翻译
 * （MCP/平台工具库经 AgentRuntimeConfig.tools），本接缝保留给测试注入替身工具
 * （如敏感工具 HITL 用例）；生产代码不应再依赖它注册业务工具（改挂平台工具库）。
 */
public interface RuntimeToolContributor {

    /**
     * 向 toolkit 注册工具（装配时调用一次，实例常驻期间不重复注册）
     */
    void contribute(Toolkit toolkit);
}
