package com.wuji.kidora.ai.common.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务键与 BIGINT 主键生成器（本地简易雪花）。
 *
 * @author liudy
 */
public final class IdGenerator {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 1000);

    private IdGenerator() {
    }

    public static long nextLong() {
        long ts = System.currentTimeMillis();
        long seq = SEQUENCE.incrementAndGet() & 0xFFFFF;
        return (ts << 20) | seq;
    }

    public static String nextBizId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
