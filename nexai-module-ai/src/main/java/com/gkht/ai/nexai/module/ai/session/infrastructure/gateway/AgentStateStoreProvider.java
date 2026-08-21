package com.gkht.ai.nexai.module.ai.session.infrastructure.gateway;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话状态存储提供者：PostgresAgentStateStore 为主存储（ADR-0005 状态存储选型），
 * 注入容器 DataSource（纳入连接池），惰性单例——首次会话才在独立 schema {@code agentscope}
 * 下自动建表（CREATE IF NOT EXISTS），应用启动不产生额外数据库动作。
 *
 * <p>{@code PostgresAgentStateStore.close()} 不关外部 DataSource，实例与容器同生命周期，不调用 close()。</p>
 */
@Component
public class AgentStateStoreProvider {

    @Resource
    private DataSource dataSource;

    private final AtomicReference<AgentStateStore> storeRef = new AtomicReference<>();

    /**
     * 获取（必要时初始化）共享状态存储实例
     */
    public AgentStateStore get() {
        AgentStateStore store = storeRef.get();
        if (store == null) {
            storeRef.compareAndSet(null, new PostgresAgentStateStore(dataSource, true));
            store = storeRef.get();
        }
        return store;
    }

}
