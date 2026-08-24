package com.gkht.ai.nexai.module.ai.session.application.service;

import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpec;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecConfig;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.AgentSpecVersion;
import com.gkht.ai.nexai.module.ai.agentspec.domain.model.ToolSource;
import com.gkht.ai.nexai.module.ai.agentspec.domain.repository.AgentSpecRepository;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Channel;
import com.gkht.ai.nexai.module.ai.channel.domain.model.Model;
import com.gkht.ai.nexai.module.ai.channel.domain.repository.ChannelRepository;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.model.McpServer;
import com.gkht.ai.nexai.module.ai.mcpserver.domain.repository.McpServerRepository;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.AgentRuntimeConfig;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.SkillMountDirectory;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMaterializationGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.gkht.ai.nexai.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.CHANNEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.MODEL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SESSION_ASSEMBLE_INVALID;

/**
 * 装配指令组装器（session 应用层共享组件）：规格版本快照 + 渠道/模型 + 挂载解析 →
 * {@link AgentRuntimeConfig}。调试会话（SessionServiceImpl）与 OpenAI 兼容出口
 * （OpenAiCompatService，工单 16）共用同一装配链——挂载翻译、审计/用量采集、
 * 版本戳失效对两条入口一致。
 */
@Component
public class AgentRuntimeAssembler {

    /** 匿名用户槽位标识（无登录态的终端会话） */
    public static final String ANONYMOUS_USER = "anonymous";

    @Resource
    private AgentSpecRepository agentSpecRepository;

    @Resource
    private ChannelRepository channelRepository;

    @Resource
    private SkillRepository skillRepository;

    @Resource
    private SkillMaterializationGateway skillMaterializationGateway;

    @Resource
    private McpServerRepository mcpServerRepository;

    /**
     * 组装装配指令（租户取当前上下文）
     *
     * @param spec       规格（已按编号读取）
     * @param version    目标版本快照
     * @param userId     槽位用户（anonymous 亦可）
     * @param sessionKey 会话业务键（agentscope 槽位）
     */
    public AgentRuntimeConfig assemble(AgentSpec spec, AgentSpecVersion version,
                                       String userId, String sessionKey) {
        AgentSpecConfig config = version.getConfig();
        if (config.getModelId() == null) {
            throw exception(SESSION_ASSEMBLE_INVALID, "规格未配置模型");
        }
        Model model = requireModel(config.getModelId());
        Channel channel = requireChannel(model.getChannelId());
        if (!model.isEnabled()) {
            throw exception(SESSION_ASSEMBLE_INVALID, "模型已停用");
        }
        if (!channel.isEnabled()) {
            throw exception(SESSION_ASSEMBLE_INVALID, "渠道已停用");
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(SESSION_ASSEMBLE_INVALID, "租户上下文缺失");
        }
        String agentName = spec.getSpecCode() + "-v" + version.getVersionNo();
        return AgentRuntimeConfig.builder()
                .userId(userId).sessionKey(sessionKey).tenantId(tenantId)
                .agentId(spec.getSpecCode()).agentName(agentName)
                .specId(spec.getId()).versionNo(version.getVersionNo())
                .ownerLevel(spec.getOwnerLevel()).ownerUserId(spec.getOwnerUserId())
                .systemPrompt(config.getSystemPrompt()).maxIters(config.getMaxIters())
                .generateOptions(config.getGenerateOptions())
                .executionEnv(config.getExecutionEnv())
                .tools(config.getTools())
                .skillMounts(resolveSkillMounts(config, tenantId))
                .mcpServers(resolveMcpServers(config))
                .folders(config.getFolders())
                .channel(channel).model(model)
                .build();
    }

    /**
     * 技能引用 → 挂载目录分组：每个 skillId 读聚合（跨聚合只读）取当前版本内容，
     * 幂等物化（内容比对一致跳过落盘）后按物化父目录分组；指纹 = skillId@versionNo 串
     * （版本戳数据源——技能推新版本即失效重建，新会话用新内容）。
     */
    private List<SkillMountDirectory> resolveSkillMounts(AgentSpecConfig config, Long tenantId) {
        if (config.getSkillIds().isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> namesByDir = new LinkedHashMap<>();
        Map<String, List<String>> fingerprintsByDir = new LinkedHashMap<>();
        for (Long skillId : config.getSkillIds()) {
            Skill skill = skillRepository.findById(skillId);
            if (skill == null) {
                throw exception(SESSION_ASSEMBLE_INVALID, "挂载的技能不存在（编号 " + skillId + "）");
            }
            if (!skill.hasVersion()) {
                throw exception(SESSION_ASSEMBLE_INVALID, "挂载的技能尚无版本（" + skill.getName() + "）");
            }
            SkillVersion currentVersion = skillRepository.listVersions(skillId).stream()
                    .filter(v -> v.getVersionNo() == skill.getCurrentVersionNo())
                    .findFirst()
                    .orElseThrow(() -> exception(SESSION_ASSEMBLE_INVALID,
                            "挂载的技能当前版本快照缺失（" + skill.getName() + "）"));
            String dir = skillMaterializationGateway.materialize(skill, tenantId,
                    currentVersion.getContent());
            String parent = Path.of(dir).getParent().toString();
            namesByDir.computeIfAbsent(parent, k -> new ArrayList<>()).add(skill.getName());
            fingerprintsByDir.computeIfAbsent(parent, k -> new ArrayList<>())
                    .add(skillId + "@" + skill.getCurrentVersionNo());
        }
        return namesByDir.entrySet().stream()
                .map(entry -> SkillMountDirectory.of(entry.getKey(), entry.getValue(),
                        String.join(",", fingerprintsByDir.get(entry.getKey()))))
                .toList();
    }

    /**
     * MCP 挂载 → 聚合本体列表：不存在/已停用为配置性缺失（数据一致性问题应在管理面暴露），
     * 装配显式报错；连接不可达为运行性缺失，由网关装配期降级跳过（工单 13 语义定案）。
     */
    private List<McpServer> resolveMcpServers(AgentSpecConfig config) {
        List<McpServer> servers = new ArrayList<>();
        for (var mount : config.getTools()) {
            if (mount.source() != ToolSource.MCP) {
                continue;
            }
            McpServer server = mcpServerRepository.findById(mount.sourceId());
            if (server == null) {
                throw exception(SESSION_ASSEMBLE_INVALID,
                        "挂载的 MCP Server 不存在（编号 " + mount.sourceId() + "）");
            }
            if (!server.isEnabled()) {
                throw exception(SESSION_ASSEMBLE_INVALID,
                        "挂载的 MCP Server 已停用（" + server.getName() + "）");
            }
            servers.add(server);
        }
        return servers;
    }

    private Model requireModel(Long modelId) {
        Model model = channelRepository.findModelById(modelId);
        if (model == null) {
            throw exception(MODEL_NOT_EXISTS);
        }
        return model;
    }

    private Channel requireChannel(Long channelId) {
        Channel channel = channelRepository.findById(channelId);
        if (channel == null) {
            throw exception(CHANNEL_NOT_EXISTS);
        }
        return channel;
    }

}
