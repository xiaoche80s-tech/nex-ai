package com.gkht.ai.nexai.module.ai.shared.util;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;

/**
 * 采集线程的租户上下文恢复（工单 14）：审计/用量 middleware 在 reactor 调度线程落库，
 * 线程可能缺失租户上下文——以采集维度（RuntimeContext extra.tenantId）恢复后执行、
 * finally 清理防串扰。audit/usage 两聚合共用。
 */
public final class TenantExecution {

    private TenantExecution() {
    }

    /** 恢复租户上下文执行动作，结束后清理（finally） */
    public static void withTenant(Long tenantId, Runnable action) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            action.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

}
