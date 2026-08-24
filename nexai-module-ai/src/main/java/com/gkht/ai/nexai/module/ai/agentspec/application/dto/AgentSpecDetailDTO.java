package com.gkht.ai.nexai.module.ai.agentspec.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体规格详情 DTO（编辑面回填用）：主体元数据 + 全量草稿配置平铺，
 * 与创建/更新命令同构，前端表单可直接回填并原样回传。
 */
@Schema(description = "管理后台 - 智能体规格详情 DTO")
@Data
public class AgentSpecDetailDTO {

    @Schema(description = "规格编号", example = "1")
    private Long id;

    @Schema(description = "规格名称", example = "客服助手")
    private String name;

    @Schema(description = "业务编码（创建后不可变）", example = "customer-service")
    private String specCode;

    @Schema(description = "归属层级", example = "TENANT")
    private String ownerLevel;

    @Schema(description = "归属用户编号（用户级 = 创建者），非用户级为 null")
    private Long ownerUserId;

    @Schema(description = "图标标识", example = "ep:service")
    private String icon;

    @Schema(description = "是否有草稿")
    private Boolean hasDraft;

    @Schema(description = "当前生效版本号（当前版本指针），null = 从未发布")
    private Integer currentVersionNo;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    // —— 草稿配置平铺（与创建/更新命令同构；无草稿时配置项为 null） ——

    @Schema(description = "模型引用编号，草稿态可空")
    private Long modelId;

    @Schema(description = "自描述")
    private String description;

    @Schema(description = "系统提示")
    private String systemPrompt;

    @Schema(description = "推理参数：最大迭代轮数")
    private Integer maxIters;

    @Schema(description = "调用参数：温度")
    private Double temperature;

    @Schema(description = "调用参数：核采样阈值")
    private Double topP;

    @Schema(description = "调用参数：单次生成的最大 tokens")
    private Integer maxTokens;

    @Schema(description = "技能引用列表")
    private List<Long> skillIds;

    @Schema(description = "工具挂载列表")
    private List<ToolMountDTO> tools;

    @Schema(description = "规格私有文件夹挂载列表")
    private List<FolderMountDTO> folders;

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
