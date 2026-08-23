package com.gkht.ai.nexai.module.ai.session.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 会话转换器：领域模型/DO → 出参 DTO。
 * 领域 → DO 转换在 RepositoryImpl 经 toDataObject 手写（字段少，不引 MapStruct 复杂映射）。
 * DO → DTO 供列表轻量读写分离直查路径使用（specCode 由应用服务按规格补充填充）。
 */
@Mapper(componentModel = "spring")
public interface SessionConverter {

    SessionDTO toDTO(Session session);

    List<SessionDTO> toDTOList(List<Session> list);

    default PageResult<SessionDTO> toDTOPage(PageResult<Session> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

    SessionDTO toDTOFromDO(SessionDO dataObject);

    List<SessionDTO> toDTOListFromDO(List<SessionDO> list);

    default PageResult<SessionDTO> toDTOPageFromDO(PageResult<SessionDO> page) {
        return new PageResult<>(toDTOListFromDO(page.getList()), page.getTotal());
    }

}
