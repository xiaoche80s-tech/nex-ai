package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 规格私有文件夹挂载值对象纯 JUnit（工单 18）：name 合法目录段、文件清单
 * path 逃逸/哈希校验、数量上限与重复路径拒绝、指纹与物化目标段。
 */
class FolderMountTest {

    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("合法挂载：类型/目录段名/多级相对路径文件")
    void acceptsValidMount() {
        FolderMount mount = assertDoesNotThrow(() -> FolderMount.of(FolderType.ASSET, "product-faq.2026",
                List.of(FolderFile.of("faq.md", "http://f/1", HASH, 12L),
                        FolderFile.of("docs/guide.md", "http://f/2", "b".repeat(64), 40L))));
        assertEquals(FolderType.ASSET, mount.getType());
        assertEquals("product-faq.2026", mount.getName());
        assertEquals(2, mount.getFiles().size());
    }

    @Test
    @DisplayName("类型缺省与目录段名非法（. / .. / 斜杠 / 空格 / 中文）被拒绝")
    void rejectsInvalidTypeAndName() {
        IllegalArgumentException noType = assertThrows(IllegalArgumentException.class,
                () -> FolderMount.of(null, "faq", List.of(FolderFile.of("a", "u", HASH, 1L))));
        assertEquals("文件夹挂载必须声明类型（ASSET/TOOLSET）", noType.getMessage());

        for (String bad : new String[]{null, "", "..", ".", "a/b", "a b", "目录", "-x", "_x"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FolderMount.of(FolderType.TOOLSET, bad,
                            List.of(FolderFile.of("a", "u", HASH, 1L))), "非法名：" + bad);
            assertEquals("文件夹名必须为合法目录段（字母或数字开头，"
                    + "仅含字母/数字/点/下划线/连字符，1~64 位）：" + bad, ex.getMessage());
        }
    }

    @Test
    @DisplayName("文件清单：空清单/超上限/相对路径重复被拒绝")
    void validatesFileList() {
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> FolderMount.of(FolderType.ASSET, "faq", List.of()));
        assertEquals("文件夹挂载必须携带文件清单（faq）", empty.getMessage());

        List<FolderFile> tooMany = IntStream.rangeClosed(1, 129)
                .mapToObj(i -> FolderFile.of("f" + i, "u" + i, HASH, 1L)).toList();
        IllegalArgumentException overflow = assertThrows(IllegalArgumentException.class,
                () -> FolderMount.of(FolderType.ASSET, "faq", tooMany));
        assertEquals("单文件夹文件数不能超过 128", overflow.getMessage());

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> FolderMount.of(FolderType.ASSET, "faq",
                        List.of(FolderFile.of("a.md", "u1", HASH, 1L),
                                FolderFile.of("a.md", "u2", HASH, 1L))));
        assertEquals("文件夹内文件相对路径不能重复（faq）", duplicate.getMessage());
    }

    @Test
    @DisplayName("文件条目：绝对路径 / .. 逃逸 / 盘符 / 非法哈希被拒绝")
    void validatesFilePathAndHash() {
        for (String badPath : new String[]{null, "", "/abs.md", "../escape.md", "a/../b.md",
                "a/./b.md", "c:\\win.md", "a:b.md", "a//b.md"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> FolderFile.of(badPath, "u", HASH, 1L), "非法路径：" + badPath);
        }
        for (String badHash : new String[]{null, "", "xyz", "A".repeat(64)}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> FolderFile.of("a.md", "u", badHash, 1L), "非法哈希：" + badHash);
            assertEquals("文件夹文件必须携带合法的 SHA-256 内容哈希（a.md）", ex.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> FolderFile.of("a.md", null, HASH, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> FolderFile.of("a.md", "u", HASH, -1L));
    }

    @Test
    @DisplayName("物化目标段：ASSET → knowledge，TOOLSET → toolsets；指纹含类型/名/哈希")
    void mapsTargetSegmentAndFingerprint() {
        FolderMount asset = FolderMount.of(FolderType.ASSET, "faq",
                List.of(FolderFile.of("a.md", "u1", "a".repeat(64), 1L)));
        FolderMount toolset = FolderMount.of(FolderType.TOOLSET, "scripts",
                List.of(FolderFile.of("a.md", "u1", "a".repeat(64), 1L)));
        assertEquals("knowledge", asset.targetSegment());
        assertEquals("toolsets", toolset.targetSegment());

        // 内容变化（哈希）→ 指纹变化（版本戳失效数据源）
        assertEquals(asset.fingerprint(), toolset.fingerprint().replace("TOOLSET:scripts", "ASSET:faq"));
        FolderMount changed = FolderMount.of(FolderType.ASSET, "faq",
                List.of(FolderFile.of("a.md", "u1", "b".repeat(64), 1L)));
        org.junit.jupiter.api.Assertions.assertNotEquals(asset.fingerprint(), changed.fingerprint());
    }

}
