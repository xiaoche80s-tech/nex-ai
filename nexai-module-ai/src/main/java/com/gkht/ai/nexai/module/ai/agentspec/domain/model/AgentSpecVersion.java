package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 智能体规格版本快照实体（充血模型，零框架依赖）：一次发布固化的全量四层配置，
 * 一旦发布即不可变——运行体按版本快照构建（装配时逐层消费，任何变更都从重新发布产生），
 * 编辑只进草稿、不触碰既有快照。
 *
 * <p>版本号从 1 起按规格内严格递增（发布次序），无上一版本与回退编辑语义；
 * 同一规格内的版本号唯一（DB 唯一索引兜底并发）。快照按规格聚合，
 * 由 {@link AgentSpec} 聚合根登记产生、经聚合仓储持久化。</p>
 */
public class AgentSpecVersion {

    /** 备注长度上限（字符） */
    static final int NOTE_MAX_LENGTH = 255;

    /** 编号，未落库时为 null */
    private Long id;
    /** 所属规格编号（聚合根引用，按聚合持久化） */
    private final Long specId;
    /** 版本号（规格内严格递增，1 起；对外展示与运行寻址用，发布后不可变） */
    private final Integer versionNo;
    /** 全量四层配置快照（agent 层/模型调用层/挂载层/执行环境层） */
    private final AgentSpecConfig config;
    /** 发布备注，可空 */
    private final String note;
    /** 创建时间，由持久化填充，新建时为 null */
    private LocalDateTime createTime;

    private AgentSpecVersion(Long id, Long specId, Integer versionNo, AgentSpecConfig config,
                             String note, LocalDateTime createTime) {
        this.id = id;
        this.specId = specId;
        this.versionNo = versionNo;
        this.config = config;
        this.note = note;
        this.createTime = createTime;
    }

    /**
     * 发布首个版本（版本号固定为 1）。发布校验集中在 {@link AgentSpec#publish}，
     * 本工厂仅做版本结构校验。
     *
     * @param specId 所属规格编号
     * @param config 全量配置快照，不能为 null
     * @param note   发布备注，可空
     */
    public static AgentSpecVersion createFirst(Long specId, AgentSpecConfig config, String note) {
        return create(specId, 1, config, note);
    }

    /**
     * 发布后续版本（版本号 = 现有最大 + 1）。快照一旦创建即不可变，
     * 本类不提供任何修改入口。
     *
     * @param specId    所属规格编号
     * @param versionNo 版本号，须 >= 1
     * @param config    全量配置快照，不能为 null
     * @param note      发布备注，可空
     */
    public static AgentSpecVersion create(Long specId, Integer versionNo, AgentSpecConfig config,
                                          String note) {
        if (specId == null) {
            throw new IllegalArgumentException("版本快照必须归属规格");
        }
        if (versionNo == null || versionNo < 1) {
            throw new IllegalArgumentException("版本号必须为正整数");
        }
        if (config == null) {
            throw new IllegalArgumentException("版本快照必须携带全量配置");
        }
        String normalizedNote = normalizeNullable(note);
        if (normalizedNote != null && normalizedNote.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("发布备注不能超过 " + NOTE_MAX_LENGTH + " 个字符");
        }
        return new AgentSpecVersion(null, specId, versionNo, config, normalizedNote, null);
    }

    /**
     * 从持久化数据重建实体（Repository 专用，字段原样恢复）
     */
    public static AgentSpecVersion reconstitute(Long id, Long specId, Integer versionNo,
                                                AgentSpecConfig config, String note,
                                                LocalDateTime createTime) {
        return new AgentSpecVersion(id, specId, versionNo, config, note, createTime);
    }

    /** 可空文本规范化：空白归 null，其余去首尾空白 */
    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    public Long getId() {
        return id;
    }

    public Long getSpecId() {
        return specId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    /** 全量四层配置快照，不可变 */
    public AgentSpecConfig getConfig() {
        return config;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentSpecVersion other)) {
            return false;
        }
        // 实体按身份（编号）判等；未落库的实体只与自身相等
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
