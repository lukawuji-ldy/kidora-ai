package com.wuji.kidora.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在独立线程跑阻塞调用；HTTP 取消时不 interrupt 工作线程。
 *
 * @author liudy
 */
public final class DetachedBlockingMono {

    private static final Logger log = LoggerFactory.getLogger(DetachedBlockingMono.class);
    private static final AtomicInteger SEQ = new AtomicInteger();

    private DetachedBlockingMono() {
    }

    public static <T> Mono<T> fromCallable(Callable<T> call) {
        return Mono.create(sink -> {
            Thread worker = new Thread(() -> {
                try {
                    sink.success(call.call());
                } catch (Throwable e) {
                    sink.error(e instanceof RuntimeException re ? re : new RuntimeException(e));
                }
            }, "kidora-io-" + SEQ.incrementAndGet());
            worker.setDaemon(true);
            worker.start();
            sink.onCancel(() -> log.warn(
                    "subscriber cancelled; {} continues", worker.getName()));
        });
    }
}
