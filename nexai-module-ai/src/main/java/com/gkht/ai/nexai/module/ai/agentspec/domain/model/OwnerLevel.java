package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 规格归属层级：决定可见性、编辑权与 workspace 落盘布局（归属谁，workspace 挂在谁的树下）。
 *
 * <p>MVP 开放 TENANT（租户内可见）与 USER（仅创建者可见可编辑）；
 * PLATFORM 字段先建，跨租户可见性与运营入口后置（与平台托管渠道同期开放）。</p>
 */
public enum OwnerLevel {

    /** 平台级：全租户可见只读、仅平台运营方编辑；workspace 挂 {@code platform/} 树下 */
    PLATFORM,

    /** 租户级（默认）：租户内可见；workspace 挂 {@code t{tenantId}/} 树下 */
    TENANT,

    /** 用户级：仅创建者可见可编辑；workspace 挂 {@code t{tenantId}/u{userId}/} 树下（物理目录即隔离） */
    USER

}
