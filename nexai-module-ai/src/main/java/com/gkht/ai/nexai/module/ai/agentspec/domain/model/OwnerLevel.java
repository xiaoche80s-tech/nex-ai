package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 规格归属层级（ADR-0006 决策 8）：决定可见性、编辑权与 workspace 落盘布局
 * （归属谁，workspace 挂在谁的树下，见 ADR-0007 决策 3）。
 *
 * <p>M1 开放 TENANT（租户内可见）与 USER（仅创建者可见可编辑）；
 * PLATFORM 字段先建，跨租户可见性与运营入口 M2+ 开放（与工单 24 同期）。</p>
 */
public enum OwnerLevel {

    /** 平台级：全租户可见只读、仅平台运营方编辑；workspace 挂 {@code platform/} 树下 */
    PLATFORM,

    /** 租户级（默认）：租户内可见；workspace 挂 {@code t{tenantId}/} 树下，记忆 per-user */
    TENANT,

    /** 用户级：仅创建者可见可编辑；workspace 挂 {@code t{tenantId}/u{userId}/} 树下（物理目录即隔离） */
    USER

}
