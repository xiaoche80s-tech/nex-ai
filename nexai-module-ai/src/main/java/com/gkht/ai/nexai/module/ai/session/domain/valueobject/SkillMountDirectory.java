package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import java.util.List;
import java.util.Objects;

/**
 * Skill 挂载目录值对象（不可变，按值判等）：装配指令的技能挂载解析结果——
 * 物化基目录（{@code {skillsRoot}/t{tenantId}[/u{userId}]/skills}，目录下每个子目录一个技能）
 * + 该目录下挂载的技能名列表 + 指纹（{@code skillId@versionNo} 串，参与常驻实例版本戳：
 * 技能推新版本即失效重建，新会话用新内容）。
 *
 * <p>由应用层在组装装配指令时解析（跨聚合只读 skill + 幂等物化），基础设施层翻译为
 * agentscope {@code FileSystemSkillRepository}（按基目录）+ {@code SkillFilter.only}（按名收敛）。</p>
 */
public final class SkillMountDirectory {

    /** 物化基目录（绝对路径，目录下每子目录一个技能，agentscope 文件仓库契约） */
    private final String baseDir;
    /** 该目录下挂载的技能名列表 */
    private final List<String> skillNames;
    /** 指纹：skillId@versionNo 逗号串（版本戳数据源——技能内容变化即失效重建） */
    private final String fingerprint;

    private SkillMountDirectory(String baseDir, List<String> skillNames, String fingerprint) {
        this.baseDir = baseDir;
        this.skillNames = skillNames;
        this.fingerprint = fingerprint;
    }

    public static SkillMountDirectory of(String baseDir, List<String> skillNames, String fingerprint) {
        if (baseDir == null || baseDir.isBlank()) {
            throw new IllegalArgumentException("技能挂载基目录不能为空");
        }
        if (skillNames == null || skillNames.isEmpty()) {
            throw new IllegalArgumentException("技能挂载目录必须携带技能名列表");
        }
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("技能挂载目录必须携带指纹（版本戳数据源）");
        }
        return new SkillMountDirectory(baseDir, List.copyOf(skillNames), fingerprint);
    }

    public String getBaseDir() {
        return baseDir;
    }

    public List<String> getSkillNames() {
        return skillNames;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillMountDirectory other)) {
            return false;
        }
        return baseDir.equals(other.baseDir) && skillNames.equals(other.skillNames)
                && fingerprint.equals(other.fingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseDir, skillNames, fingerprint);
    }

}
