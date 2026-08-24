package com.gkht.ai.nexai.module.ai.session.interfaces.sse;

import com.gkht.ai.nexai.module.ai.session.domain.valueobject.RuntimeEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;

/**
 * {@code Flux<RuntimeEvent> → SseEmitter} 桥（壳与帧分离）：订阅调度、错误收尾、
 * complete 的壳只此一份；帧形状由各入口的 {@link FrameEncoder} 薄 adapter 决定
 * （调试台命名事件帧、OpenAI 兼容出口 chunk+DONE 帧、终端页面 AG-UI 帧）。
 */
public final class SseBridge {

    /** 帧编码器：壳不关心帧形状，只约定收尾语义 */
    public interface FrameEncoder {

        /** 正常事件帧；IO 失败（客户端断开）抛 {@link IOException}，壳统一 completeWithError */
        void encode(SseEmitter emitter, RuntimeEvent event) throws IOException;

        /** 流出错时的收尾帧（message 为错误消息，null 兜底由编码器决定） */
        void encodeError(SseEmitter emitter, String message) throws IOException;

        /** 流正常结束的收尾（无需收尾帧的入口空实现，如调试台） */
        void onComplete(SseEmitter emitter) throws IOException;
    }

    private SseBridge() {
    }

    /**
     * 桥接：无超时 emitter + boundedElastic 订阅；错误发收尾帧后 complete（不挂死连接），
     * 客户端断开时 completeWithError 触发订阅取消（框架 doFinally 释放常驻实例引用）。
     */
    public static SseEmitter bridge(Flux<RuntimeEvent> events, FrameEncoder encoder) {
        SseEmitter emitter = new SseEmitter(0L); // 无超时（智能体轮次可能较长）
        events.subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> send(emitter, () -> encoder.encode(emitter, event)),
                        error -> {
                            send(emitter, () -> encoder.encodeError(emitter, error.getMessage()));
                            emitter.complete();
                        },
                        () -> {
                            send(emitter, () -> encoder.onComplete(emitter));
                            emitter.complete();
                        });
        return emitter;
    }

    /** 发送失败（客户端断开等 IO 问题）即放弃后续帧并触发订阅取消 */
    private static void send(SseEmitter emitter, IoAction action) {
        try {
            action.run();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
