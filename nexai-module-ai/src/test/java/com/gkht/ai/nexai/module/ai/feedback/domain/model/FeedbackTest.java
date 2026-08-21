package com.gkht.ai.nexai.module.ai.feedback.domain.model;

import com.gkht.ai.nexai.module.ai.feedback.domain.exception.FeedbackStatusTransitionException;
import com.gkht.ai.nexai.module.ai.feedback.domain.valueobject.FeedbackStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feedback 聚合根与 FeedbackStatus 值对象的纯 JUnit 测试（S3 接缝，不继承任何基类）。
 */
class FeedbackTest {

    @Nested
    @DisplayName("create 工厂")
    class CreateTest {

        @Test
        void createShouldInitPendingStatusAndImmutableFields() {
            Feedback feedback = Feedback.create("模型回复乱码", List.of("http://f1.png", "http://f2.png"),
                    "session-001", 1L);

            assertEquals(null, feedback.getId());
            assertEquals("模型回复乱码", feedback.getContent());
            assertEquals(List.of("http://f1.png", "http://f2.png"), feedback.getScreenshotUrls());
            assertEquals("session-001", feedback.getSessionId());
            assertEquals(FeedbackStatus.PENDING, feedback.getStatus());
            assertEquals(1L, feedback.getSubmitterId());
        }

        @Test
        void createShouldTolerateNullScreenshotsAndSession() {
            Feedback feedback = Feedback.create("无截图反馈", null, null, 1L);

            assertEquals(List.of(), feedback.getScreenshotUrls());
            assertEquals(null, feedback.getSessionId());
        }

        @Test
        void createShouldRejectBlankContent() {
            assertThrows(IllegalArgumentException.class, () -> Feedback.create("  ", null, null, 1L));
            assertThrows(IllegalArgumentException.class, () -> Feedback.create(null, null, null, 1L));
        }

        @Test
        void screenshotUrlsShouldBeUnmodifiable() {
            Feedback feedback = Feedback.create("内容", List.of("http://f1.png"), null, 1L);

            assertThrows(UnsupportedOperationException.class,
                    () -> feedback.getScreenshotUrls().add("http://f3.png"));
        }

    }

    @Nested
    @DisplayName("状态流转矩阵")
    class TransitionTest {

        @ParameterizedTest
        @CsvSource({
                "PENDING, PROCESSING, true",
                "PENDING, RESOLVED, true",
                "PENDING, CLOSED, true",
                "PENDING, PENDING, false",
                "PROCESSING, PENDING, true",
                "PROCESSING, RESOLVED, true",
                "PROCESSING, CLOSED, true",
                "PROCESSING, PROCESSING, false",
                "RESOLVED, PENDING, true",
                "RESOLVED, PROCESSING, true",
                "RESOLVED, CLOSED, true",
                "RESOLVED, RESOLVED, false",
                "CLOSED, PENDING, false",
                "CLOSED, PROCESSING, false",
                "CLOSED, RESOLVED, false",
                "CLOSED, CLOSED, false",
        })
        void canTransitionToMatrix(String fromName, String targetName, boolean expected) {
            FeedbackStatus from = FeedbackStatus.valueOf(fromName);
            FeedbackStatus target = FeedbackStatus.valueOf(targetName);

            assertEquals(expected, from.canTransitionTo(target));
        }

        @ParameterizedTest
        @EnumSource(FeedbackStatus.class)
        void closedShouldNeverTransitionOut(FeedbackStatus target) {
            assertFalse(FeedbackStatus.CLOSED.canTransitionTo(target));
        }

        @Test
        void shouldNeverTransitionToNull() {
            assertFalse(FeedbackStatus.PENDING.canTransitionTo(null));
        }

        private Feedback feedbackWithStatus(FeedbackStatus status) {
            return Feedback.reconstitute(100L, "内容", List.of(), null, status, 1L, null);
        }

        @Test
        void transitionToShouldUpdateStatusWhenLegal() {
            Feedback feedback = feedbackWithStatus(FeedbackStatus.PENDING);

            feedback.transitionTo(FeedbackStatus.PROCESSING);

            assertEquals(FeedbackStatus.PROCESSING, feedback.getStatus());
        }

        @Test
        void transitionToShouldThrowWhenIllegal() {
            Feedback feedback = feedbackWithStatus(FeedbackStatus.CLOSED);

            assertThrows(FeedbackStatusTransitionException.class,
                    () -> feedback.transitionTo(FeedbackStatus.PROCESSING));
            assertEquals(FeedbackStatus.CLOSED, feedback.getStatus());
        }

        @Test
        void transitionToShouldThrowWhenTargetIsNull() {
            Feedback feedback = feedbackWithStatus(FeedbackStatus.PENDING);

            assertThrows(FeedbackStatusTransitionException.class, () -> feedback.transitionTo(null));
        }

    }

    @Nested
    @DisplayName("FeedbackStatus 编解码")
    class FeedbackStatusCodecTest {

        @ParameterizedTest
        @CsvSource({
                "10, PENDING",
                "20, PROCESSING",
                "30, RESOLVED",
                "40, CLOSED",
        })
        void ofShouldResolveKnownCodes(Integer code, String expectedName) {
            assertEquals(FeedbackStatus.valueOf(expectedName), FeedbackStatus.of(code));
        }

        @Test
        void ofShouldReturnNullForUnknownOrNullCode() {
            assertEquals(null, FeedbackStatus.of(null));
            assertEquals(null, FeedbackStatus.of(99));
        }

        @Test
        void everyStatusShouldHaveDistinctCode() {
            long distinctCodes = java.util.Arrays.stream(FeedbackStatus.values())
                    .map(FeedbackStatus::getCode).distinct().count();

            assertEquals(FeedbackStatus.values().length, distinctCodes);
            assertTrue(FeedbackStatus.PENDING.getCode() > 0);
        }

    }

}
