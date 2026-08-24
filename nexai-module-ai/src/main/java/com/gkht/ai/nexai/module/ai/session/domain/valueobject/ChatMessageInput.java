package com.gkht.ai.nexai.module.ai.session.domain.valueobject;

import java.util.Objects;

/**
 * 出口消息入参值对象（不可变，按值判等，工单 16）：OpenAI 兼容出口的请求消息
 * （role + content 扁平口径——无状态出口，客户端带全量历史；tool 相关消息 MVP 不支持）。
 */
public final class ChatMessageInput {

    /** 消息角色：system / user / assistant */
    private final String role;
    /** 消息文本内容 */
    private final String content;

    private ChatMessageInput(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessageInput of(String role, String content) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("消息必须声明角色（system/user/assistant）");
        }
        if (content == null) {
            throw new IllegalArgumentException("消息内容不能为 null（可为空串）");
        }
        return new ChatMessageInput(role.strip().toLowerCase(java.util.Locale.ROOT), content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessageInput other)) {
            return false;
        }
        return Objects.equals(role, other.role) && Objects.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

}
