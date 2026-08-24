package com.gkht.ai.nexai.module.ai.channel.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;

/**
 * 模型装配端口（CONTEXT.md「模型装配端口」词条，工单 21 候选 6；原命名 ModelRegistry
 * 与 agentscope 静态注册表重名，改名 ChatModelProvider）：以渠道 + 模型标识换取
 * agentscope ChatModel 实例。两个消费者（本聚合的连通性探测、session 聚合的运行时
 * 装配）经此接口协作——探测通过即等价于装配可用。
 *
 * <p>接口位于 infrastructure（而非 domain）：签名携带 agentscope 类型（返回
 * {@code io.agentscope.core.model.Model}），两个消费者均在 infrastructure 层，
 * 不为形式纯洁把框架类型拉进 domain 接口（工单 19 grilling Q16 已定）。
 * 实现见 {@link ChatModelFactory}（ADR-0001：模型构造直用 agentscope 五家扩展）。</p>
 */
public interface ChatModelProvider {

    /**
     * 构造指定渠道下某模型的 ChatModel 实例（每次调用新建——agentscope 线程模型：
     * 单 agent 单 session 串行，实例不跨会话复用）
     */
    io.agentscope.core.model.Model create(Channel channel, String modelId);

}
