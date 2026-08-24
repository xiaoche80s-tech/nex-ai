package com.gkht.ai.nexai.module.ai.agentspec.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 文件夹内单个文件条目值对象（不可变，按值判等）：相对路径 + 存储地址 + 内容哈希 + 字节数。
 *
 * <p>内容寻址（contentHash，SHA-256 hex）是「版本快照不可变」与「FileApi 对象可变」之间的桥——
 * 快照固化的是 url + hash 清单；装配物化时按哈希比对，磁盘/远端内容与清单不符即显式报错，
 * 不静默沿用旧物化（工单 18 设计注记）。文件实体存 infra FileApi（跨模块 api），
 * 不建独立资产聚合，规格私有、不可跨规格复用。</p>
 *
 * @param path        文件夹内相对路径（可含子目录，拒绝绝对路径与 .. 逃逸）
 * @param url         存储地址（infra FileApi 返回的访问路径）
 * @param contentHash 内容 SHA-256（64 位十六进制），内容寻址与物化比对依据
 * @param size        文件字节数
 */
public record FolderFile(String path, String url, String contentHash, long size) {

    /** 相对路径长度上限 */
    static final int PATH_MAX_LENGTH = 512;
    /** 存储地址长度上限 */
    static final int URL_MAX_LENGTH = 1024;
    /** SHA-256 hex 长度 */
    private static final int SHA256_HEX_LENGTH = 64;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public FolderFile {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("文件夹文件必须声明相对路径");
        }
        if (path.length() > PATH_MAX_LENGTH || path.startsWith("/")
                || path.startsWith("\\") || path.contains(":") || path.contains("\\")) {
            throw new IllegalArgumentException("文件夹文件路径必须为目录内相对路径：" + path);
        }
        // 逐段拒绝 .. 逃逸（段级校验而非字符串包含，避免误伤形如 a..b 的文件名）
        for (String segment : path.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("文件夹文件路径不能包含 . 或 .. 段：" + path);
            }
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("文件夹文件必须携带存储地址");
        }
        if (url.length() > URL_MAX_LENGTH) {
            throw new IllegalArgumentException("文件夹文件存储地址超长");
        }
        if (contentHash == null || !HEX_PATTERN.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("文件夹文件必须携带合法的 SHA-256 内容哈希（" + path + "）");
        }
        if (size < 0) {
            throw new IllegalArgumentException("文件夹文件字节数不能为负（" + path + "）");
        }
    }

    public static FolderFile of(String path, String url, String contentHash, long size) {
        return new FolderFile(path, url, contentHash, size);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FolderFile other
                && Objects.equals(path, other.path) && Objects.equals(url, other.url)
                && Objects.equals(contentHash, other.contentHash) && size == other.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, url, contentHash, size);
    }

}
