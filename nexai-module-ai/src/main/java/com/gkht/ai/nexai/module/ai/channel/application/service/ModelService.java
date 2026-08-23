package com.gkht.ai.nexai.module.ai.channel.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.channel.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.channel.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.channel.application.query.ModelPageQuery;

/**
 * 模型应用服务：挂接在渠道下的模型元数据 CRUD 与启停。
 */
public interface ModelService {

    /** 登记模型（同渠道内标识唯一），返回编号 */
    Long createModel(ModelCreateCommand command);

    /** 更新模型元数据（全量替换，可改挂渠道） */
    void updateModel(ModelUpdateCommand command);

    /** 启停模型 */
    void updateModelStatus(ModelUpdateStatusCommand command);

    /** 删除模型 */
    void deleteModel(Long id);

    /** 分页查询（轻量读写分离，补充渠道名） */
    PageResult<ModelDTO> getModelPage(ModelPageQuery query);

    /** 模型详情（补充渠道名） */
    ModelDTO getModel(Long id);

}
