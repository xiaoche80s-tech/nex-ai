package com.gkht.ai.nexai.module.ai.agentspec.application.service;

import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecCreateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecDraftCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecPublishCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.command.AgentSpecUpdateCommand;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecConfigDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecDetailDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.dto.AgentSpecVersionDTO;
import com.gkht.ai.nexai.module.ai.agentspec.application.query.AgentSpecPageQuery;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecPublishWithoutDraftException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.exception.AgentSpecVersionNotExistsException;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.converter.AgentSpecConverter;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.dataobject.AgentSpecVersionDO;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecMapper;
import com.gkht.ai.nexai.module.ai.agentspec.infrastructure.mapper.AgentSpecVersionMapper;
import com.gkht.ai.nexai.module.ai.model.domain.model.Model;
import com.gkht.ai.nexai.module.ai.model.domain.repository.ModelRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_PUBLISH_WITHOUT_DRAFT;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.AGENT_SPEC_VERSION_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;

/**
 * 智能体规格应用服务实现。写走聚合（Repository 端口），读按轻量读写分离经 Mapper 直查并批量补充模型信息。
 *
 * <p>模型引用存在性在编辑与发布两个时点都校验（草稿保存后模型仍可能被删除）；
 * 启用性不校验——停用模型只影响运行时装配（工单 06 兜底），不影响规格迭代。</p>
 */
@Service
@Validated
public class AgentSpecServiceImpl implements AgentSpecService {

    @Resource
    private AgentSpecRepository agentSpecRepository;

    @Resource
    private AgentSpecMapper agentSpecMapper;

    @Resource
    private AgentSpecVersionMapper agentSpecVersionMapper;

    @Resource
    private AgentSpecConverter agentSpecConverter;

    @Resource
    private ModelRepository modelRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpec(AgentSpecCreateCommand command) {
        requireModel(command.getModelId());
        AgentSpec spec = AgentSpec.create(command.getName(), command.getDescription(), command.getIcon(),
                toConfig(command));
        return agentSpecRepository.save(spec);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpec(AgentSpecUpdateCommand command) {
        AgentSpec spec = requireSpec(command.getId());
        requireModel(command.getModelId());
        spec.editDraft(command.getName(), command.getDescription(), command.getIcon(), toConfig(command));
        agentSpecRepository.save(spec);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSpec(Long id) {
        requireSpec(id);
        agentSpecRepository.deleteByIdCascade(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer publishSpec(AgentSpecPublishCommand command) {
        AgentSpec spec = requireSpec(command.getId());
        AgentSpecVersion version;
        try {
            version = spec.publish(command.getRemark());
        } catch (AgentSpecPublishWithoutDraftException ex) {
            throw exception(AGENT_SPEC_PUBLISH_WITHOUT_DRAFT);
        }
        // 快照引用的模型已被删除时拒绝发布（事务内校验，失败整体回滚，不产出悬空引用）
        requireModel(version.getConfig().getModelId());
        // 聚合状态（指针前移、草稿清空）与版本快照同一事务落库
        agentSpecRepository.save(spec);
        agentSpecRepository.createVersion(version);
        return version.getVersionNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void switchDefaultVersion(AgentSpecSwitchVersionCommand command) {
        AgentSpec spec = requireSpec(command.getId());
        try {
            spec.switchDefaultVersion(command.getVersionNo());
        } catch (AgentSpecVersionNotExistsException ex) {
            throw exception(AGENT_SPEC_VERSION_NOT_EXISTS);
        }
        agentSpecRepository.save(spec);
    }

    @Override
    public PageResult<AgentSpecDTO> getSpecPage(AgentSpecPageQuery query) {
        PageResult<AgentSpecDO> page = agentSpecMapper.selectPage(query, query.getName());
        PageResult<AgentSpecDTO> result = agentSpecConverter.toDTOPage(page);
        fillRowModelNames(page.getList(), result.getList());
        return result;
    }

    @Override
    public AgentSpecDetailDTO getSpec(Long id) {
        AgentSpec spec = requireSpec(id);
        AgentSpecDetailDTO detail = agentSpecConverter.toDetailDTO(spec);
        detail.setDraft(spec.hasDraft() ? agentSpecConverter.toConfigDTO(spec.getDraft()) : null);
        detail.setCurrentVersion(findCurrentVersion(spec.getId(), spec.getCurrentVersionNo()));

        List<AgentSpecConfigDTO> configs = new ArrayList<>();
        if (detail.getDraft() != null) {
            configs.add(detail.getDraft());
        }
        if (detail.getCurrentVersion() != null) {
            configs.add(detail.getCurrentVersion().getConfig());
        }
        fillConfigModelNames(configs);
        return detail;
    }

    @Override
    public List<AgentSpecVersionDTO> getVersionList(Long specId) {
        requireSpec(specId);
        List<AgentSpecVersionDTO> versions = agentSpecRepository.findVersionsBySpecId(specId).stream()
                .map(agentSpecConverter::toVersionDTO)
                .toList();
        fillConfigModelNames(versions.stream().map(AgentSpecVersionDTO::getConfig).toList());
        return versions;
    }

    /**
     * 查询规格当前默认版本并转出参，从未发布为 null
     */
    private AgentSpecVersionDTO findCurrentVersion(Long specId, Integer currentVersionNo) {
        if (currentVersionNo == null) {
            return null;
        }
        return agentSpecRepository.findVersionsBySpecId(specId).stream()
                .filter(version -> version.getVersionNo() == currentVersionNo)
                .findFirst()
                .map(agentSpecConverter::toVersionDTO)
                .orElse(null);
    }

    /**
     * 列表行模型名批量填充：行模型引用优先取草稿（正在编辑的内容），
     * 无草稿取默认版本快照——草稿 JSON 直接从 DO 解析，默认版本一次批量查询后内存匹配
     */
    private void fillRowModelNames(List<AgentSpecDO> dataObjects, List<AgentSpecDTO> rows) {
        if (dataObjects.isEmpty()) {
            return;
        }
        Map<Long, AgentSpecDO> specById = dataObjects.stream()
                .collect(Collectors.toMap(AgentSpecDO::getId, spec -> spec, (a, b) -> a));
        List<Long> defaultVersionSpecIds = dataObjects.stream()
                .filter(spec -> spec.getDraft() == null && spec.getCurrentVersionNo() != null)
                .map(AgentSpecDO::getId).toList();
        Map<Long, Long> modelIdBySpecId = new HashMap<>();
        for (AgentSpecDO dataObject : dataObjects) {
            if (dataObject.getDraft() != null) {
                AgentSpecConfig draft = agentSpecConverter.jsonToConfig(dataObject.getDraft());
                if (draft != null) {
                    modelIdBySpecId.put(dataObject.getId(), draft.getModelId());
                }
            }
        }
        for (AgentSpecVersionDO version : agentSpecVersionMapper.selectListBySpecIds(defaultVersionSpecIds)) {
            AgentSpecDO spec = specById.get(version.getSpecId());
            if (spec == null || !spec.getCurrentVersionNo().equals(version.getVersionNo())) {
                continue;
            }
            AgentSpecConfig snapshot = agentSpecConverter.jsonToConfig(version.getSnapshot());
            if (snapshot != null) {
                modelIdBySpecId.putIfAbsent(version.getSpecId(), snapshot.getModelId());
            }
        }
        Map<Long, String> modelNames = modelNameMap(modelIdBySpecId.values());
        for (AgentSpecDTO row : rows) {
            row.setModelName(modelNames.get(modelIdBySpecId.get(row.getId())));
        }
    }

    /**
     * 配置出参模型名批量填充
     */
    private void fillConfigModelNames(Collection<AgentSpecConfigDTO> configs) {
        Map<Long, String> modelNames = modelNameMap(
                configs.stream().map(AgentSpecConfigDTO::getModelId).filter(Objects::nonNull).toList());
        for (AgentSpecConfigDTO config : configs) {
            config.setModelName(modelNames.get(config.getModelId()));
        }
    }

    private Map<Long, String> modelNameMap(Collection<Long> modelIds) {
        return modelRepository.findByIds(modelIds.stream().filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Model::getId, Model::getName, (a, b) -> a));
    }

    /**
     * command → 领域配置值对象（创建/编辑命令共用）
     */
    private AgentSpecConfig toConfig(AgentSpecDraftCommand command) {
        return AgentSpecConfig.of(command.getModelId(), command.getSystemPrompt(), command.getMaxIters(),
                command.getTemperature(), command.getSkillIds(), command.getKnowledgeBaseIds(),
                command.getMcpServerIds(), command.getSubagentSpecIds());
    }

    private AgentSpec requireSpec(Long id) {
        AgentSpec spec = agentSpecRepository.findById(id);
        if (spec == null) {
            throw exception(AGENT_SPEC_NOT_EXISTS);
        }
        return spec;
    }

    /**
     * 规格引用的模型必须存在（跨聚合只读校验，直接查对方 Repository）
     */
    private void requireModel(Long modelId) {
        if (modelRepository.findById(modelId) == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
    }

}
