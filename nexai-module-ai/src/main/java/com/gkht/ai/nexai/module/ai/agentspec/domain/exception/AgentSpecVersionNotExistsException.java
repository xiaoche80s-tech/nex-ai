package com.gkht.ai.nexai.module.ai.agentspec.domain.exception;

/**
 * 版本不存在：切换默认版本时给定的版本号不在该规格已发布的版本范围内。
 */
public class AgentSpecVersionNotExistsException extends RuntimeException {

    public AgentSpecVersionNotExistsException(Long specId, Integer versionNo) {
        super("规格 " + specId + " 不存在版本 v" + versionNo);
    }

}
