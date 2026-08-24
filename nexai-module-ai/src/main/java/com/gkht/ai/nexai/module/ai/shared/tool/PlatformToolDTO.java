package com.gkht.ai.nexai.module.ai.shared.tool;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 平台工具库条目 DTO（挂载编辑面候选列表）。
 */
@Schema(description = "管理后台 - 平台工具库条目 DTO")
@Data
public class PlatformToolDTO {

    @Schema(description = "稳定注册编号（挂载引用 ToolMount.sourceId 的语义）", example = "1")
    private Long id;

    @Schema(description = "条目标识", example = "sample-echo")
    private String code;

    @Schema(description = "显示名", example = "回显示例工具")
    private String name;

    @Schema(description = "条目说明")
    private String description;

    @Schema(description = "条目提供的工具名集合（挂载白名单候选面）", example = "[\"echo\",\"echo_upper\"]")
    private List<String> toolNames;

}
