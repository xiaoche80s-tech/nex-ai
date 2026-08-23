package com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper;

import com.gkht.ai.nexai.framework.common.pojo.PageParam;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.mapper.BaseMapperX;
import com.gkht.ai.nexai.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.OwnerLevel;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能体规格 Mapper。查询条件以原始参数传入，避免 infrastructure 反向依赖 application 层类型。
 */
@Mapper
public interface AgentSpecMapper extends BaseMapperX<AgentSpecDO> {

    /**
     * 分页查询（MVP 可见性口径：租户级租户内全见 + 用户级仅归属用户可见；
     * 平台级跨租户可见性后置，当前经租户插件过滤兜底）
     *
     * @param pageParam     分页参数
     * @param name          规格名称（模糊匹配），可空
     * @param specCode      业务编码（精确匹配），可空
     * @param currentUserId 当前登录用户编号（用户级可见性过滤），可空（无登录态时仅见非用户级）
     */
    default PageResult<AgentSpecDO> selectPage(PageParam pageParam, String name, String specCode,
                                               Long currentUserId) {
        LambdaQueryWrapperX<AgentSpecDO> query = new LambdaQueryWrapperX<AgentSpecDO>()
                .likeIfPresent(AgentSpecDO::getName, name)
                .eqIfPresent(AgentSpecDO::getSpecCode, specCode)
                .orderByDesc(AgentSpecDO::getId);
        String userLevel = OwnerLevel.USER.name();
        if (currentUserId == null) {
            query.ne(AgentSpecDO::getOwnerLevel, userLevel);
        } else {
            query.and(wrapper -> wrapper.ne(AgentSpecDO::getOwnerLevel, userLevel)
                    .or().eq(AgentSpecDO::getOwnerUserId, currentUserId));
        }
        return selectPage(pageParam, query);
    }

    /**
     * 同归属层级下业务编码是否已存在（创建时应用层唯一性预校验；
     * tenant_id 由租户插件自动过滤，owner_user_id 归属用户隔离）
     */
    default boolean existsBySpecCode(OwnerLevel ownerLevel, Long ownerUserId, String specCode) {
        return selectCount(new LambdaQueryWrapperX<AgentSpecDO>()
                .eq(AgentSpecDO::getOwnerLevel, ownerLevel.name())
                .eq(ownerUserId != null, AgentSpecDO::getOwnerUserId, ownerUserId)
                .isNull(ownerUserId == null, AgentSpecDO::getOwnerUserId)
                .eq(AgentSpecDO::getSpecCode, specCode)) > 0;
    }

}
