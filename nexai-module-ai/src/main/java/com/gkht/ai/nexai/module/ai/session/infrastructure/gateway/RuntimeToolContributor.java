package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import io.agentscope.core.tool.Toolkit;

/**
 * 运行时工具贡献者：agent 装配时向 Toolkit 注册工具的扩展点。
 *
 * <p>M1 无生产实现（智能体暂无工具来源）；M2 的技能挂载、MCP 挂载与工具白名单
 * 从此扩展点注入，S1 测试用它注册调试工具以验证工具调用事件流。infrastructure
 * 内部接缝（非领域概念），不进 domain 层。</p>
 */
public interface RuntimeToolContributor {

    /**
     * 向运行时 Toolkit 注册工具（实现内可多次调用 {@code toolkit.registerTool(Object)}，
     * 注册带 {@code @Tool} 注解的 POJO）
     */
    void contribute(Toolkit toolkit);

}
