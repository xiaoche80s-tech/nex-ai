package com.gkht.ai.nexai.module.ai.session.interfaces.sse;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SSE 桥壳直测（记录型 FrameEncoder，不经真实容器）：正常流按序发帧 + 收尾回调、
 * 错误流走 encodeError 收尾、帧形状由编码器决定（壳不掺帧内容）。
 */
class SseBridgeTest {

    /** 记录型编码器：把收到的回调记成日志行（壳的行为面即回调序列） */
    private static final class RecordingEncoder implements SseBridge.FrameEncoder {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public void encode(SseEmitter emitter, RuntimeEvent event) {
            calls.add("data:" + event.type().name());
        }

        @Override
        public void encodeError(SseEmitter emitter, String message) {
            calls.add("error:" + message);
        }

        @Override
        public void onComplete(SseEmitter emitter) {
            calls.add("done");
        }
    }

    @Test
    @DisplayName("正常流：事件按序编码，流结束触发收尾回调")
    void normalFlowDeliversFramesThenCompletion() {
        RecordingEncoder encoder = new RecordingEncoder();
        SseBridge.bridge(Flux.just(
                        RuntimeEvent.of(RuntimeEventType.AGENT_START, "{}"),
                        RuntimeEvent.of(RuntimeEventType.TEXT_BLOCK_DELTA, "{}")),
                encoder);
        await(encoder.calls, calls -> calls.equals(List.of("data:AGENT_START", "data:TEXT_BLOCK_DELTA", "done")));
    }

    @Test
    @DisplayName("错误流：encodeError 收尾且不再触发完成回调")
    void errorFlowSendsErrorFrameWithoutDone() {
        RecordingEncoder encoder = new RecordingEncoder();
        SseBridge.bridge(Flux.<RuntimeEvent>error(new RuntimeException("boom")), encoder);
        await(encoder.calls, calls -> calls.equals(List.of("error:boom")));
    }

    /** boundedElastic 异步订阅：轮询等待回调序列到位（5 秒上限） */
    private static void await(List<String> calls, java.util.function.Predicate<List<String>> expected) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (expected.test(calls)) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(expected.test(calls), "回调序列未在期限内到位，实际：" + calls);
    }
}
