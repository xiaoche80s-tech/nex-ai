package com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话 DO（贫血模型）。对话消息本体不经此表：复用 agentscope 状态存储
 * （PostgresAgentStateStore，独立 schema agentscope），本表只承载会话元数据与绑定关系。
 */
@TableName("ai_session")
@KeySequence("ai_session_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionDO extends TenantBaseDO {

    /**
     * 会话编号
     */
    @TableId
    private Long id;
    /**
     * 会话标识（agentscope 状态存储寻址键，全局唯一）
     */
    private String sessionKey;
    /**
     * 会话类型编码，见 SessionType
     */
    private Integer type;
    /**
     * 绑定的规格编号（ai_agent_spec.id）
     */
    private Long specId;
    /**
     * 绑定的规格版本号（ai_agent_spec_version.version_no）
     */
    private Integer versionNo;
    /**
     * 会话标题
     */
    private String title;
    /**
     * 推理参数覆盖：最大迭代轮数（null 沿用版本快照，克隆重跑微调落点）
     */
    private Integer overrideMaxIters;
    /**
     * 推理参数覆盖：温度（null 沿用版本快照）
     */
    private Double overrideTemperature;
    /**
     * 已发送的消息轮数
     */
    private Integer messageRounds;

}
