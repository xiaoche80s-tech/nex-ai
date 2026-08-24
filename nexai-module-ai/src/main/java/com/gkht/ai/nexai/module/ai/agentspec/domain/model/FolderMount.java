package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 规格私有文件夹挂载值对象（不可变，按值判等）：挂载层的 folders 通道（工单 18）——
 * 类型（{@link FolderType} ASSET 资料文件夹 / TOOLSET 工具集文件夹）+ 目标子目录名 +
 * 文件清单（{@link FolderFile}：path / url / contentHash / size，内容寻址）。
 *
 * <p>资产形态为<b>规格私有</b>：不建独立资产聚合、不可跨规格复用，清单随版本快照固化
 * （发布时 url + hash 清单进 {@code ai_agent_spec_version.config}，不可变）；上传的文件
 * 在发布前属草稿态。物化落位（依据 2026-08-23 workspace 调研）：ASSET → workspace
 * {@code knowledge/<name>/}（agentscope 原生预留位），TOOLSET → {@code toolsets/<name>/}
 * （自定义目录，不占用框架硬编码路径）；装配物化时按 contentHash 内容比对，
 * 不匹配显式报错不静默沿用旧物化。</p>
 *
 * <p>仅 workspaceEnabled 时可挂载（进 {@link AgentSpecConfig} 校验链）；
 * TOOLSET 脚本<b>执行</b>受既有沙箱能力链约束（执行须沙箱模式），
 * 挂载本身不强制开沙箱（文件可只读引用）。</p>
 */
public final class FolderMount {

    /** 文件夹名 = 目标子目录段：字母或数字开头，仅含字母/数字/点/下划线/连字符，1~64 位 */
    static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    /** 单文件夹文件数上限（防误传目录树撑爆快照） */
    static final int FILES_MAX_SIZE = 128;

    /** 文件夹类型（决定物化落位 knowledge/ 或 toolsets/） */
    private final FolderType type;
    /** 目标子目录名（workspace 下的 knowledge/&lt;name&gt; 或 toolsets/&lt;name&gt;） */
    private final String name;
    /** 文件清单（相对路径 → 存储地址 + 内容哈希 + 字节数） */
    private final List<FolderFile> files;

    private FolderMount(FolderType type, String name, List<FolderFile> files) {
        this.type = type;
        this.name = name;
        this.files = files;
    }

    /**
     * 构建文件夹挂载
     *
     * @param type  文件夹类型，不能为 null
     * @param name  目标子目录名，须为合法目录段（同规格内唯一性由 {@link AgentSpecConfig} 校验）
     * @param files 文件清单，不能为空且最多 {@value FILES_MAX_SIZE} 项，文件夹内相对路径不重复
     */
    public static FolderMount of(FolderType type, String name, List<FolderFile> files) {
        if (type == null) {
            throw new IllegalArgumentException("文件夹挂载必须声明类型（ASSET/TOOLSET）");
        }
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("文件夹名必须为合法目录段（字母或数字开头，"
                    + "仅含字母/数字/点/下划线/连字符，1~64 位）：" + name);
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("文件夹挂载必须携带文件清单（" + name + "）");
        }
        if (files.size() > FILES_MAX_SIZE) {
            throw new IllegalArgumentException("单文件夹文件数不能超过 " + FILES_MAX_SIZE);
        }
        if (files.stream().map(FolderFile::path).distinct().count() != files.size()) {
            throw new IllegalArgumentException("文件夹内文件相对路径不能重复（" + name + "）");
        }
        return new FolderMount(type, name, List.copyOf(files));
    }

    public FolderType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public List<FolderFile> getFiles() {
        return files;
    }

    /** 物化目标子目录段：ASSET → knowledge，TOOLSET → toolsets */
    public String targetSegment() {
        return type == FolderType.ASSET ? "knowledge" : "toolsets";
    }

    /**
     * 指纹（常驻实例版本戳数据源，工单 08/18）：type:name@hash 串——
     * 清单内容（哈希集合）变化即失效重建。
     */
    public String fingerprint() {
        return type + ":" + name + "@" + files.stream()
                .map(file -> file.path() + "=" + file.contentHash())
                .collect(Collectors.joining(","));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FolderMount other)) {
            return false;
        }
        return type == other.type && name.equals(other.name) && files.equals(other.files);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name, files);
    }

}
