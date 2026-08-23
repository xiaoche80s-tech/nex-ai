package com.gkht.ai.nexai.module.ai.channel.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.channel.infrastructure.dataobject.ChannelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 渠道 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface ChannelMapper extends BaseMapperX<ChannelDO> {

    /**
     * 分页查询（tenant_id 由租户插件自动过滤）
     *
     * @param name     渠道名称（模糊匹配），可空
     * @param provider 提供商类型编码（精确），可空
     * @param enabled  是否启用，可空
     */
    default PageResult<ChannelDO> selectPage(PageParam pageParam, String name, String provider,
                                             Boolean enabled) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ChannelDO>()
                .likeIfPresent(ChannelDO::getName, name)
                .eqIfPresent(ChannelDO::getProvider, provider)
                .eqIfPresent(ChannelDO::getEnabled, enabled)
                .orderByDesc(ChannelDO::getId));
    }

}
