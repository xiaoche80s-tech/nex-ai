package com.gkht.ai.nexai.module.ai.model.domain.model;

import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelOwnerType;
import com.gkht.ai.nexai.module.ai.model.domain.valueobject.ChannelProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 纯 JUnit：渠道聚合领域行为（零框架依赖，直接构造实体测试）。
 */
public class ChannelTest {

    private static Channel createDefault() {
        return Channel.create("OpenAI 主渠道", ChannelProvider.OPENAI,
                "https://api.openai.com/v1", "sk-1234567890abcdef", ChannelOwnerType.TENANT);
    }

    @Nested
    @DisplayName("创建")
    class CreateTest {

        @Test
        @DisplayName("创建成功：默认启用、归属租户、名称与端点去空白、密钥保留")
        public void createSuccess() {
            Channel channel = Channel.create("  OpenAI 主渠道  ", ChannelProvider.OPENAI,
                    "  https://api.openai.com/v1  ", "  sk-1234567890abcdef  ", ChannelOwnerType.TENANT);

            assertNull(channel.getId());
            assertEquals("OpenAI 主渠道", channel.getName());
            assertEquals(ChannelProvider.OPENAI, channel.getProvider());
            assertEquals("https://api.openai.com/v1", channel.getBaseUrl());
            assertEquals("sk-1234567890abcdef", channel.getApiKey());
            assertTrue(channel.isEnabled());
            assertEquals(ChannelOwnerType.TENANT, channel.getOwnerType());
            assertNull(channel.getCreateTime());
        }

        @Test
        @DisplayName("密钥可空：ollama 等本地服务无密钥")
        public void createWithoutApiKey() {
            Channel channel = Channel.create("本地 Ollama", ChannelProvider.OLLAMA,
                    "http://127.0.0.1:11434", null, ChannelOwnerType.TENANT);

            assertNull(channel.getApiKey());
            assertNull(channel.maskedApiKey());
        }

        @Test
        @DisplayName("密钥纯空白按未配置处理")
        public void createWithBlankApiKey() {
            Channel channel = Channel.create("本地 Ollama", ChannelProvider.OLLAMA,
                    "http://127.0.0.1:11434", "   ", ChannelOwnerType.TENANT);

            assertNull(channel.getApiKey());
        }

        @Test
        @DisplayName("名称/提供商/端点/归属缺失时拒绝创建")
        public void createWithIllegalArguments() {
            assertThrows(IllegalArgumentException.class, () -> Channel.create(null,
                    ChannelProvider.OPENAI, "https://api.openai.com/v1", "sk-1", ChannelOwnerType.TENANT));
            assertThrows(IllegalArgumentException.class, () -> Channel.create("  ",
                    ChannelProvider.OPENAI, "https://api.openai.com/v1", "sk-1", ChannelOwnerType.TENANT));
            assertThrows(IllegalArgumentException.class, () -> Channel.create("渠道", null,
                    "https://api.openai.com/v1", "sk-1", ChannelOwnerType.TENANT));
            assertThrows(IllegalArgumentException.class, () -> Channel.create("渠道",
                    ChannelProvider.OPENAI, "  ", "sk-1", ChannelOwnerType.TENANT));
            assertThrows(IllegalArgumentException.class, () -> Channel.create("渠道",
                    ChannelProvider.OPENAI, "https://api.openai.com/v1", "sk-1", null));
        }

    }

    @Nested
    @DisplayName("更新")
    class UpdateTest {

        @Test
        @DisplayName("更新基础信息：密钥传 null 保留原值")
        public void updateWithoutApiKeyKeepsOriginal() {
            Channel channel = createDefault();

            channel.update("改名后的渠道", ChannelProvider.DASHSCOPE,
                    "https://dashscope.aliyuncs.com/compatible-mode/v1", null);

            assertEquals("改名后的渠道", channel.getName());
            assertEquals(ChannelProvider.DASHSCOPE, channel.getProvider());
            assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", channel.getBaseUrl());
            assertEquals("sk-1234567890abcdef", channel.getApiKey());
        }

        @Test
        @DisplayName("更新基础信息：传入新密钥则覆盖")
        public void updateWithNewApiKey() {
            Channel channel = createDefault();

            channel.update("渠道", ChannelProvider.OPENAI,
                    "https://api.openai.com/v1", "sk-newkey987654321");

            assertEquals("sk-newkey987654321", channel.getApiKey());
        }

        @Test
        @DisplayName("更新时名称/提供商/端点缺失被拒绝")
        public void updateWithIllegalArguments() {
            Channel channel = createDefault();

            assertThrows(IllegalArgumentException.class,
                    () -> channel.update(" ", ChannelProvider.OPENAI, "https://api.openai.com/v1", null));
            assertThrows(IllegalArgumentException.class,
                    () -> channel.update("渠道", null, "https://api.openai.com/v1", null));
            assertThrows(IllegalArgumentException.class,
                    () -> channel.update("渠道", ChannelProvider.OPENAI, null, null));
        }

    }

    @Nested
    @DisplayName("启停")
    class EnableTest {

        @Test
        @DisplayName("停用后可再启用")
        public void disableThenEnable() {
            Channel channel = createDefault();

            channel.disable();
            assertFalse(channel.isEnabled());

            channel.enable();
            assertTrue(channel.isEnabled());
        }

    }

    @Nested
    @DisplayName("密钥脱敏")
    class MaskTest {

        @Test
        @DisplayName("长密钥保留前 4 后 4 位")
        public void maskLongApiKey() {
            Channel channel = createDefault();

            assertEquals("sk-1****cdef", channel.maskedApiKey());
        }

        @Test
        @DisplayName("短密钥（≤8 位）全遮蔽")
        public void maskShortApiKey() {
            Channel channel = Channel.create("渠道", ChannelProvider.OPENAI,
                    "https://api.openai.com/v1", "sk-1234", ChannelOwnerType.TENANT);

            assertEquals("****", channel.maskedApiKey());
        }

    }

    @Test
    @DisplayName("聚合根按编号判等：未落库的聚合只与自身相等")
    public void equalityByIdentity() {
        Channel transientChannel = createDefault();
        assertNotEquals(transientChannel, createDefault());

        Channel persisted = Channel.reconstitute(1L, "渠道", ChannelProvider.OPENAI,
                "https://api.openai.com/v1", "sk-1", true, ChannelOwnerType.TENANT, null);
        Channel sameId = Channel.reconstitute(1L, "另一个渠道", ChannelProvider.OLLAMA,
                "http://127.0.0.1:11434", null, false, ChannelOwnerType.TENANT, null);
        assertEquals(persisted, sameId);
    }

}
