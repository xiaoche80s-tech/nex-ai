package com.gkht.ai.nexai.module.ai.model.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ModelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface ModelMapper extends BaseMapperX<ModelDO> {

    default PageResult<ModelDO> selectPage(PageParam pageParam, Long channelId, String modelId,
                                           String name, String capability, Boolean enabled) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ModelDO>()
                .eqIfPresent(ModelDO::getChannelId, channelId)
                .likeIfPresent(ModelDO::getModelId, modelId)
                .likeIfPresent(ModelDO::getName, name)
                // capabilities 存 JSON 数组字符串，按标签词包含匹配
                .likeIfPresent(ModelDO::getCapabilities, capability)
                .eqIfPresent(ModelDO::getEnabled, enabled)
                .orderByDesc(ModelDO::getId));
    }

    default ModelDO selectByChannelIdAndModelId(Long channelId, String modelId) {
        return selectOne(ModelDO::getChannelId, channelId, ModelDO::getModelId, modelId);
    }

}
