package com.gkht.ai.nexai.module.ai.model.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelCreateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateCommand;
import com.gkht.ai.nexai.module.ai.model.application.command.ModelUpdateStatusCommand;
import com.gkht.ai.nexai.module.ai.model.application.dto.ConnectivityTestDTO;
import com.gkht.ai.nexai.module.ai.model.application.dto.ModelDTO;
import com.gkht.ai.nexai.module.ai.model.application.query.ModelPageQuery;

import java.util.List;

/**
 * 模型管理应用服务：渠道下登记模型元数据、启停与连通性测试。
 */
public interface ModelService {

    /**
     * 登记模型（同渠道下模型标识唯一），返回编号
     */
    Long createModel(ModelCreateCommand command);

    /**
     * 更新模型元数据（可改挂渠道；改渠道时校验新渠道存在）
     */
    void updateModel(ModelUpdateCommand command);

    /**
     * 启用/停用模型
     */
    void updateModelStatus(ModelUpdateStatusCommand command);

    /**
     * 删除模型（逻辑删除）
     */
    void deleteModel(Long id);

    /**
     * 模型分页（含所属渠道名称/提供商补充）
     */
    PageResult<ModelDTO> getModelPage(ModelPageQuery query);

    /**
     * 模型详情
     */
    ModelDTO getModel(Long id);

    /**
     * 启用模型精简列表（智能体规格编辑等下拉选择用）
     */
    List<ModelDTO> getEnabledModelList();

    /**
     * 连通性测试：取模型所属渠道的凭据做一次轻量真实调用，返回成败/耗时/说明。
     * 真实外呼不进事务。
     */
    ConnectivityTestDTO testConnectivity(Long id);

}
