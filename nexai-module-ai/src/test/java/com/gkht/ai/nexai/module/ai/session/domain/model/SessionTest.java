package com.gkht.ai.nexai.module.ai.session.domain.model;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.SessionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话聚合根纯 JUnit 测试（S3：domain 层不继承任何基类，AGENTS.md 约定的唯一例外）。
 */
class SessionTest {

    @Test
    @DisplayName("发起调试会话：生成 dbg- 前缀全局唯一标识，类型为 DEBUG，轮次从 0 起")
    void startDebug_generatesKeyAndType() {
        Session session = Session.startDebug(1L, 3, "联调");

        assertNull(session.getId());
        assertNotNull(session.getSessionKey());
        assertTrue(session.getSessionKey().startsWith("dbg-"), "调试会话标识应以 dbg- 开头");
        assertEquals(SessionType.DEBUG, session.getType());
        assertEquals(1L, session.getSpecId());
        assertEquals(3, session.getVersionNo());
        assertEquals("联调", session.getTitle());
        assertEquals(0, session.getMessageRounds());

        Session another = Session.startDebug(1L, 3, "联调");
        assertNotEquals(session.getSessionKey(), another.getSessionKey(), "会话标识必须全局唯一");
    }

    @Test
    @DisplayName("发起调试会话：规格为空或版本号越界被拒绝")
    void startDebug_validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> Session.startDebug(null, 1, null));
        assertThrows(IllegalArgumentException.class, () -> Session.startDebug(1L, 0, null));
    }

    @Test
    @DisplayName("标题规范化：空白归 null，超长被拒绝")
    void startDebug_normalizesTitle() {
        assertNull(Session.startDebug(1L, 1, "  ").getTitle());
        assertEquals("联调", Session.startDebug(1L, 1, " 联调 ").getTitle());
        assertThrows(IllegalArgumentException.class,
                () -> Session.startDebug(1L, 1, "x".repeat(Session.TITLE_MAX_LENGTH + 1)));
    }

    @Test
    @DisplayName("记录消息轮次：逐次递增")
    void recordMessageRound_increments() {
        Session session = Session.startDebug(1L, 1, null);
        session.recordMessageRound();
        session.recordMessageRound();
        assertEquals(2, session.getMessageRounds());
    }

    @Test
    @DisplayName("重建：持久化字段原样恢复")
    void reconstitute_restoresFields() {
        Session session = Session.reconstitute(9L, "dbg-exist", SessionType.DEBUG,
                5L, 2, "历史会话", 8, 0.7, 7, "1", null);
        assertEquals(9L, session.getId());
        assertEquals("dbg-exist", session.getSessionKey());
        assertEquals(SessionType.DEBUG, session.getType());
        assertEquals(5L, session.getSpecId());
        assertEquals(2, session.getVersionNo());
        assertEquals("历史会话", session.getTitle());
        assertEquals(8, session.getOverrideMaxIters());
        assertEquals(0.7, session.getOverrideTemperature());
        assertEquals(7, session.getMessageRounds());
        assertEquals("1", session.getCreatorUserId());
    }

    @Test
    @DisplayName("发起调试会话：推理参数覆盖形式校验（范围外拒绝）")
    void startDebug_validatesOverrides() {
        assertThrows(IllegalArgumentException.class,
                () -> Session.startDebug(1L, 1, null, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> Session.startDebug(1L, 1, null, 101, null));
        assertThrows(IllegalArgumentException.class,
                () -> Session.startDebug(1L, 1, null, null, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> Session.startDebug(1L, 1, null, null, 2.1));
        assertEquals(5, Session.startDebug(1L, 1, null, 5, 1.5).getOverrideMaxIters());
    }

    @Test
    @DisplayName("克隆：新会话标识、恒为 DEBUG、继承绑定与覆盖、轮次归零、标题派生")
    void cloneAsDebug_derivesNewDebugSession() {
        Session source = Session.startDebug(1L, 3, "联调", null, null);
        source.recordMessageRound();
        source.recordMessageRound();

        Session cloned = source.cloneAsDebug(null, null, null);

        assertNull(cloned.getId());
        assertNotEquals(source.getSessionKey(), cloned.getSessionKey(), "克隆必须换新的会话标识（独立状态槽位）");
        assertEquals(SessionType.DEBUG, cloned.getType());
        assertEquals(1L, cloned.getSpecId());
        assertEquals(3, cloned.getVersionNo());
        assertEquals(0, cloned.getMessageRounds(), "克隆会话轮数归零");
        assertTrue(cloned.getTitle().contains("（克隆）") && cloned.getTitle().contains("联调"),
                "缺省标题应派生自源标题");

        // 显式覆盖优先；未指定的维度沿用源会话的覆盖值
        Session overridden = source.cloneAsDebug("高温对照", 9, 1.8);
        assertEquals("高温对照", overridden.getTitle());
        assertEquals(9, overridden.getOverrideMaxIters());
        assertEquals(1.8, overridden.getOverrideTemperature());

        Session inherited = source.cloneAsDebug(null, 9, null);
        assertNull(inherited.getOverrideTemperature(), "未指定维度沿用源会话覆盖（源为 null 则仍为 null）");
    }

    @Test
    @DisplayName("克隆：源标题缺失时以会话标识兜底，超长默认标题防截断溢出")
    void cloneAsDebug_fallbackTitle() {
        Session untitled = Session.startDebug(1L, 1, null);
        assertTrue(untitled.cloneAsDebug(null, null, null).getTitle().startsWith("dbg-"));

        Session longTitled = Session.reconstitute(1L, "dbg-x", SessionType.DEBUG, 1L, 1,
                "标".repeat(Session.TITLE_MAX_LENGTH), null, null, 0, "1", null);
        String clonedTitle = longTitled.cloneAsDebug(null, null, null).getTitle();
        assertTrue(clonedTitle.length() <= Session.TITLE_MAX_LENGTH, "克隆默认标题不得超长");
        assertTrue(clonedTitle.endsWith("（克隆）"), "超长截断应保留后缀");
    }

    @Test
    @DisplayName("会话类型编码：往返解析一致，非法编码被拒绝")
    void sessionType_roundTripsCode() {
        for (SessionType type : SessionType.values()) {
            assertEquals(type, SessionType.of(type.getCode()));
        }
        assertThrows(IllegalArgumentException.class, () -> SessionType.of(99));
    }

}
