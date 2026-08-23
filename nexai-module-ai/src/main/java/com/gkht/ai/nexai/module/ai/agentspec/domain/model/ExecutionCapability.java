package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 沙箱执行能力（执行环境层）：仅沙箱模式可开放——本地文件系统模式下 shell 在宿主机裸奔，
 * 官方文档明示生产禁跑不可信代码。
 *
 * <p>运行时由网关映射为沙箱镜像选择（能力组合 → 服务级镜像配置），域内只承载语义。</p>
 */
public enum ExecutionCapability {

    /** shell 命令执行 */
    SHELL,

    /** Python 代码执行 */
    PYTHON,

    /** Node.js 代码执行 */
    NODE

}
