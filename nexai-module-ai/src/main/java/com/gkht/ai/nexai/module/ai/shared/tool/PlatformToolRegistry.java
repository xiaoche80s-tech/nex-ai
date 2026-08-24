package com.gkht.ai.nexai.module.ai.shared.tool;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台工具库注册表：收集全部 {@link PlatformToolEntry} Bean，提供按编号寻址
 * （挂载翻译用）与全量列表（挂载编辑面用）。编号冲突在启动期即失败暴露。
 */
@Component
public class PlatformToolRegistry {

    private final Map<Long, PlatformToolEntry> entriesById;

    public PlatformToolRegistry(List<PlatformToolEntry> discovered) {
        Map<Long, PlatformToolEntry> indexed = new LinkedHashMap<>();
        for (PlatformToolEntry entry : discovered) {
            PlatformToolEntry existed = indexed.put(entry.getId(), entry);
            if (existed != null) {
                throw new IllegalStateException(String.format(
                        "平台工具库条目编号冲突：#%s 同时被 %s 与 %s 声明",
                        entry.getId(), existed.getCode(), entry.getCode()));
            }
        }
        this.entriesById = indexed;
    }

    /** 按编号寻条目（挂载翻译），不存在返回 null */
    public PlatformToolEntry findById(Long id) {
        return id == null ? null : entriesById.get(id);
    }

    /** 全量条目（按编号排序，挂载编辑面候选列表） */
    public List<PlatformToolEntry> listEntries() {
        return entriesById.values().stream()
                .sorted(Comparator.comparing(PlatformToolEntry::getId))
                .toList();
    }

}
