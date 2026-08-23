package com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ModelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface ModelMapper extends BaseMapperX<ModelDO> {

    /**
     * 分页查询（tenant_id 由租户插件自动过滤）
     *
     * @param channelId 所属渠道编号，可空
     * @param modelId   模型标识（模糊匹配），可空
     * @param name      显示名（模糊匹配），可空
     * @param enabled   是否启用，可空
     */
    default PageResult<ModelDO> selectPage(PageParam pageParam, Long channelId, String modelId,
                                           String name, Boolean enabled) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ModelDO>()
                .eqIfPresent(ModelDO::getChannelId, channelId)
                .likeIfPresent(ModelDO::getModelId, modelId)
                .likeIfPresent(ModelDO::getName, name)
                .eqIfPresent(ModelDO::getEnabled, enabled)
                .orderByDesc(ModelDO::getId));
    }

}
