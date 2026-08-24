package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 智能体规格配置平铺 DTO 基类（四层，按 agentscope 分层）：与创建/更新命令同构的表单形态。
 * 草稿与版本快照的配置同构，编辑回填（{@link AgentSpecDetailDTO}）与版本只读预览
 * （{@link AgentSpecVersionDetailDTO}）共用同一平铺；无对应配置时各字段为 null。
 */
@Schema(description = "管理后台 - 智能体规格配置平铺 DTO（四层，草稿与版本快照同构）")
@Data
public class AgentSpecFlatConfigDTO {

    // —— agent 层（模型与提示） ——

    @Schema(description = "模型引用编号，草稿态可空")
    private Long modelId;

    @Schema(description = "自描述")
    private String description;

    @Schema(description = "系统提示")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数")
    private Integer maxIters;

    // —— 模型调用层 ——

    @Schema(description = "调用参数：温度")
    private Double temperature;

    @Schema(description = "调用参数：核采样阈值")
    private Double topP;

    @Schema(description = "调用参数：单次生成的最大 tokens")
    private Integer maxTokens;

    // —— 挂载层 ——

    @Schema(description = "技能引用列表")
    private List<Long> skillIds;

    @Schema(description = "工具挂载列表")
    private List<ToolMountDTO> tools;

    @Schema(description = "规格私有文件夹挂载列表")
    private List<FolderMountDTO> folders;

    // —— 执行环境层 ——

    @Schema(description = "是否启用 workspace")
    private Boolean workspaceEnabled;

    @Schema(description = "是否启用 Docker 沙箱")
    private Boolean sandboxEnabled;

    @Schema(description = "沙箱内开放的执行能力")
    private List<String> capabilities;

    @Data
    public static class ToolMountDTO {

        @Schema(description = "挂载来源（MCP/PLATFORM）", example = "MCP")
        private String source;

        @Schema(description = "来源编号", example = "1")
        private Long sourceId;

        @Schema(description = "放行面：空 = 该来源全部工具")
        private List<String> allowedTools;

        @Schema(description = "敏感面：调用前挂起等人工审批")
        private List<String> sensitiveTools;

    }

    @Data
    public static class FolderMountDTO {

        @Schema(description = "文件夹类型（ASSET/TOOLSET）", example = "ASSET")
        private String type;

        @Schema(description = "目标子目录名", example = "faq")
        private String name;

        @Schema(description = "文件清单")
        private List<FolderFileDTO> files;

    }

    @Data
    public static class FolderFileDTO {

        @Schema(description = "文件夹内相对路径", example = "faq.md")
        private String path;

        @Schema(description = "存储地址")
        private String url;

        @Schema(description = "内容 SHA-256")
        private String contentHash;

        @Schema(description = "文件字节数")
        private Long size;

    }

}
