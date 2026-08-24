package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.ChatMessageInput;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * OpenAI 兼容出口应用服务端口（工单 16）：/app-api/ai/openai/chat/completions（流式）——
 * 请求 model 字段路由到租户智能体（specCode），客户端管理全量历史（无状态出口）。
 */
public interface OpenAiCompatService {

    /**
     * 流式对话：装配指令按 model 路由的规格解析，返回 OPENAI_CHUNK 事件流
     * （错误以 SESSION_ERROR 事件收尾，调用方分流渲染）
     *
     * @param specCode 路由的智能体业务编码（请求 model 字段）
     * @param messages 客户端全量消息历史（system/user/assistant）
     * @param requestId 请求标识（响应 id 与出口会话槽位数据源）
     */
    Flux<RuntimeEvent> streamChatCompletions(String specCode, List<ChatMessageInput> messages,
                                             String requestId);

}
