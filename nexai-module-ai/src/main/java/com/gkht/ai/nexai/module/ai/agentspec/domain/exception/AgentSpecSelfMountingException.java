package com.gkht.ai.nexai.module.ai.agentspec.domain.exception;

/**
 * 规格把自身挂载为子智能体：自引用会形成循环 spawn，保存草稿时被拒绝。
 */
public class AgentSpecSelfMountingException extends RuntimeException {

    public AgentSpecSelfMountingException(Long specId) {
        super("规格 " + specId + " 不能把自己挂载为子智能体");
    }

}
