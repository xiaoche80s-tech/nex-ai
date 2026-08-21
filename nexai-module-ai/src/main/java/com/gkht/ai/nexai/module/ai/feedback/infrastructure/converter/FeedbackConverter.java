package com.gkht.ai.nexai.module.ai.feedback.infrastructure.converter;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.feedback.application.dto.FeedbackDTO;
import com.gkht.ai.nexai.module.ai.feedback.domain.model.Feedback;
import com.gkht.ai.nexai.module.ai.feedback.infrastructure.dataobject.FeedbackDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 问题反馈转换器：领域模型 → DO、DO/领域模型 → 出参 DTO。
 * DO → 领域模型（聚合重建）在 RepositoryImpl 经 Feedback.reconstitute 完成。
 */
@Mapper(componentModel = "spring")
public interface FeedbackConverter {

    @Mapping(source = "status.code", target = "status")
    FeedbackDO toDataObject(Feedback feedback);

    FeedbackDTO toDTO(FeedbackDO feedbackDO);

    @Mapping(source = "status.code", target = "status")
    FeedbackDTO toDTOFromDomain(Feedback feedback);

    List<FeedbackDTO> toDTOList(List<FeedbackDO> list);

    default PageResult<FeedbackDTO> toDTOPage(PageResult<FeedbackDO> page) {
        return new PageResult<>(toDTOList(page.getList()), page.getTotal());
    }

}
