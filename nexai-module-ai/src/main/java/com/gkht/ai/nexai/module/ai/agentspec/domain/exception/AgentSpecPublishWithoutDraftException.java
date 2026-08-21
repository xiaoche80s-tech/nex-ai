package com.gkht.ai.nexai.module.ai.agentspec.domain.exception;

/**
 * 无草稿可发布：规格当前没有草稿（刚发布过或从未创建），发布语义要求草稿 → 发布 → 再编辑生成新草稿。
 */
public class AgentSpecPublishWithoutDraftException extends RuntimeException {

    public AgentSpecPublishWithoutDraftException(Long specId) {
        super("规格 " + specId + " 当前没有可发布的草稿，请先编辑生成新草稿");
    }

}
