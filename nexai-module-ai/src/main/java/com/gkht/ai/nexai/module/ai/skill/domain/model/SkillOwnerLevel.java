package com.gkht.ai.nexai.module.ai.skill.domain.model;

/**
 * Skill 资产归属层级（ADR-0005）：值域 TENANT/USER（PLATFORM 已废弃——
 * Git 源下沉为租户级同步导入，工单 29）。归属决定可见性与物化目录隔离。
 */
public enum SkillOwnerLevel {
    /** 租户级（租户内共享） */
    TENANT,
    /** 用户级（仅归属用户可见） */
    USER
}
