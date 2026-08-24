package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

/**
 * 规格私有文件夹类型（工单 18）：
 *
 * <ul>
 *   <li>{@link #ASSET} 资料文件夹——静态参考文件（智能体经文件工具直读），
 *       物化到 workspace {@code knowledge/<name>/}（agentscope 原生预留位）；</li>
 *   <li>{@link #TOOLSET} 工具集文件夹——自定义工具脚本，物化到 workspace
 *       {@code toolsets/<name>/}（自定义目录，不占用框架硬编码路径）。</li>
 * </ul>
 *
 * <p>两类均为静态文件挂载（区别于 Skill 能力包与 MCP 工具，这是规格自有的文件资产）；
 * TOOLSET 内脚本的<b>执行</b>受执行环境校验链约束（执行须沙箱模式），
 * 挂载本身不强制开沙箱（文件可只读引用）。</p>
 */
public enum FolderType {

    /** 资料文件夹：物化到 workspace knowledge/&lt;name&gt;/，文件工具直读 */
    ASSET,

    /** 工具集文件夹：物化到 workspace toolsets/&lt;name&gt;/，脚本执行受沙箱校验链约束 */
    TOOLSET

}
