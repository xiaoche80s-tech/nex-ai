package com.gkht.ai.nexai.module.ai.session.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.session.application.dto.SessionDTO;
import com.gkht.ai.nexai.module.ai.session.domain.model.Session;
import com.gkht.ai.nexai.module.ai.session.infrastructure.dataobject.SessionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 会话转换器：领域模型 → DO、DO/领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 Session.reconstitute 完成。
 */
@Mapper(componentModel = "spring")
public interface SessionConverter {

    @Mapping(source = "type.code", target = "type")
    SessionDO toDataObject(Session session);

    @Mapping(source = "type.code", target = "type")
    SessionDTO toDTO(Session session);

    SessionDTO toDTOFromDataObject(SessionDO sessionDO);

    List<SessionDTO> toDTOList(List<SessionDO> list);

    default PageResult<SessionDTO> toDTOPage(PageResult<SessionDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

}
