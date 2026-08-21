package com.gkht.ai.nexai.module.ai.feedback.infrastructure.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantBaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 问题反馈 DO（贫血模型，JSON 数组列经 JacksonTypeHandler 映射）
 */
@TableName(value = "ai_feedback", autoResultMap = true)
@KeySequence("ai_feedback_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackDO extends TenantBaseDO {

    /**
     * 反馈编号
     */
    @TableId
    private Long id;
    /**
     * 反馈内容
     */
    private String content;
    /**
     * 截图 URL 列表（JSON 数组字符串落库）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> screenshotUrls;
    /**
     * 关联会话标识
     */
    private String sessionId;
    /**
     * 处理状态编码，见 FeedbackStatus
     */
    private Integer status;
    /**
     * 提交人用户编号
     */
    private Long submitterId;

}
