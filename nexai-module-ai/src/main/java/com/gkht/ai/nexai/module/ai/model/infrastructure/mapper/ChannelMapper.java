package com.gkht.ai.nexai.module.ai.model.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.model.infrastructure.dataobject.ChannelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface ChannelMapper extends BaseMapperX<ChannelDO> {

    default PageResult<ChannelDO> selectPage(PageParam pageParam, String name, String provider, Boolean enabled) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ChannelDO>()
                .likeIfPresent(ChannelDO::getName, name)
                .eqIfPresent(ChannelDO::getProvider, provider)
                .eqIfPresent(ChannelDO::getEnabled, enabled)
                .orderByDesc(ChannelDO::getId));
    }

}
