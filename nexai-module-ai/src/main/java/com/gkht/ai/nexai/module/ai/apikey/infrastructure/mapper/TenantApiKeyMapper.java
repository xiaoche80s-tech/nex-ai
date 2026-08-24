package com.gkht.ai.nexai.module.ai.apikey.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.apikey.infrastructure.dataobject.TenantApiKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 API Key Mapper。
 */
@Mapper
public interface TenantApiKeyMapper extends BaseMapperX<TenantApiKeyDO> {

    default PageResult<TenantApiKeyDO> selectPage(PageParam pageParam, String name) {
        return selectPage(pageParam, new LambdaQueryWrapperX<TenantApiKeyDO>()
                .likeIfPresent(TenantApiKeyDO::getName, name)
                .orderByDesc(TenantApiKeyDO::getId));
    }

}
