package com.gkht.ai.nexai.module.ai.feedback.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.dataobject.FeedbackDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问题反馈 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface FeedbackMapper extends BaseMapperX<FeedbackDO> {

    default PageResult<FeedbackDO> selectPage(PageParam pageParam, Integer status, String content) {
        return selectPage(pageParam, new LambdaQueryWrapperX<FeedbackDO>()
                .eqIfPresent(FeedbackDO::getStatus, status)
                .likeIfPresent(FeedbackDO::getContent, content)
                .orderByDesc(FeedbackDO::getId));
    }

}
