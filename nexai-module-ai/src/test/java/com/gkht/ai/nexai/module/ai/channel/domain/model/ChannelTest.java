package com.gkht.ai.nexai.module.ai.channel.domain.model;

import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.channel.domain.valueobject.ChannelProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渠道聚合根纯 JUnit：必填与长度校验、密钥语义（null 保留/空白归 null/脱敏）、启停。
 */
class ChannelTest {

    private static Channel createDefault() {
        return Channel.create("OpenAI 主渠道", ChannelProvider.OPENAI,
                "https://api.openai.com/v1", "sk-1234567890abcdef", ChannelOwnerType.TENANT);
    }

    @Test
    @DisplayName("创建成功：文本去空白、密钥保留、初始启用、未落库编号为 null")
    void createsWithNormalization() {
        Channel channel = Channel.create("  主渠道  ", ChannelProvider.OPENAI_COMPAT,
                "  https://relay.example.com/v1  ", "  sk-key  ", ChannelOwnerType.TENANT);
        assertEquals("主渠道", channel.getName());
        assertEquals("https://relay.example.com/v1", channel.getBaseUrl());
        assertEquals("sk-key", channel.getApiKey());
        assertTrue(channel.isEnabled());
        assertNull(channel.getId());
    }

    @Test
    @DisplayName("密钥语义：空白归 null（本地服务无密钥）；出参脱敏不漏明文")
    void apiKeySemantics() {
        Channel local = Channel.create("Ollama", ChannelProvider.OLLAMA,
                "http://localhost:11434", "   ", ChannelOwnerType.TENANT);
        assertNull(local.getApiKey());

        Channel withKey = createDefault();
        assertEquals("sk-1****cdef", withKey.maskedApiKey());
        assertNull(local.maskedApiKey());
    }

    @Test
    @DisplayName("更新：apiKey 传 null 保留原密钥（编辑不回传明文）；传新值覆盖")
    void updateKeepsApiKeyWhenNull() {
        Channel channel = createDefault();
        channel.update("改名", ChannelProvider.DASHSCOPE, "https://dashscope.aliyuncs.com", null);
        assertEquals("sk-1234567890abcdef", channel.getApiKey(), "未回传密钥时不应覆盖");

        channel.update("改名", ChannelProvider.DASHSCOPE, "https://dashscope.aliyuncs.com", "sk-new-key-987654321");
        assertEquals("sk-new-key-987654321", channel.getApiKey());
    }

    @Test
    @DisplayName("必填校验：名称/提供商/端点/归属缺失均被拒绝")
    void validatesBasics() {
        assertThrows(IllegalArgumentException.class, () -> Channel.create(" ", ChannelProvider.OPENAI,
                "https://x", null, ChannelOwnerType.TENANT));
        assertThrows(IllegalArgumentException.class, () -> Channel.create("名", null,
                "https://x", null, ChannelOwnerType.TENANT));
        assertThrows(IllegalArgumentException.class, () -> Channel.create("名", ChannelProvider.OPENAI,
                " ", null, ChannelOwnerType.TENANT));
        assertThrows(IllegalArgumentException.class, () -> Channel.create("名", ChannelProvider.OPENAI,
                "https://x", null, null));
    }

    @Test
    @DisplayName("长度上限：名称 64/端点 512/密钥 1024 超限被拒绝")
    void validatesLengths() {
        assertDoesNotThrow(() -> Channel.create("长".repeat(64), ChannelProvider.OPENAI,
                "https://x", null, ChannelOwnerType.TENANT));
        assertThrows(IllegalArgumentException.class, () -> Channel.create("长".repeat(65),
                ChannelProvider.OPENAI, "https://x", null, ChannelOwnerType.TENANT));

        assertThrows(IllegalArgumentException.class, () -> Channel.create("名", ChannelProvider.OPENAI,
                "https://" + "a".repeat(510), null, ChannelOwnerType.TENANT));
        assertThrows(IllegalArgumentException.class, () -> Channel.create("名", ChannelProvider.OPENAI,
                "https://x", "k".repeat(1025), ChannelOwnerType.TENANT));
    }

    @Test
    @DisplayName("启停切换：停用保留数据与密钥，仅退出来用范围")
    void enableDisable() {
        Channel channel = createDefault();
        channel.disable();
        assertEquals(false, channel.isEnabled());
        assertEquals("sk-1234567890abcdef", channel.getApiKey());
        channel.enable();
        assertTrue(channel.isEnabled());
    }

}
