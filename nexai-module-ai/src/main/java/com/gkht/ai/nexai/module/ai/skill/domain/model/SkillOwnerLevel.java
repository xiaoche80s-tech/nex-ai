package com.gkht.ai.nexai.module.ai.skill.domain.model;

/**
 * Skill 资产归属层级（ADR-0003 双轨）：TENANT/USER 级走 DB 版本链管理（本聚合实现），
 * PLATFORM 级官方 skill 库直连 Git 仓库源（后置）。归属决定可见性与物化目录隔离。
 */
public enum SkillOwnerLevel {
    /** 租户级（租户内共享） */
    TENANT,
    /** 用户级（仅归属用户可见） */
    USER,
    /** 平台级（官方 skill 库，MVP 后置） */
    PLATFORM
}
