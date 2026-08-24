package com.gkht.ai.nexai.module.ai.shared.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 内容哈希工具（SHA-256 小写 hex）：规格私有文件夹的内容寻址（工单 18）——
 * 上传签发凭证与装配物化比对共用同一口径。
 */
public final class Hashes {

    private Hashes() {
    }

    /** 计算内容 SHA-256（小写十六进制，64 位）；null 内容按空字节流处理 */
    public static String sha256Hex(byte[] content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(content == null ? new byte[0] : content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // JDK 规范保证 SHA-256 可用，此分支仅满足受检异常语法
            throw new IllegalStateException("SHA-256 摘要算法不可用", ex);
        }
    }

}
