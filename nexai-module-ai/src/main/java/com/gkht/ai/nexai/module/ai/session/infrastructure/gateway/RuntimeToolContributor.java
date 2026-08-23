package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import io.agentscope.core.tool.Toolkit;

/**
 * 运行时工具贡献者扩展点（infrastructure 接缝）：装配常驻实例时向 toolkit 注册平台业务工具
 * （@Tool 注解 POJO / ToolBase 实现）。MVP 供测试注入敏感工具与回显工具；
 * 生产接线（MCP 挂载/平台工具库）在挂载工单 12/13 经本扩展点落地。
 */
public interface RuntimeToolContributor {

    /**
     * 向 toolkit 注册工具（装配时调用一次，实例常驻期间不重复注册）
     */
    void contribute(Toolkit toolkit);
}
