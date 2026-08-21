package com.gkht.ai.nexai.module.ai.agentspec.domain.exception;

/**
 * 已发布版本不可变：任何对已发布版本的修改尝试都被拒绝（发布语义的核心不变量）。
 * 迭代请走「编辑草稿 → 发布新版本」。
 */
public class AgentSpecVersionImmutableException extends RuntimeException {

    public AgentSpecVersionImmutableException(Long specId, int versionNo) {
        super("规格 " + specId + " 的版本 v" + versionNo + " 已发布，不可修改");
    }

}
