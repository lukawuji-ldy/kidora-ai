package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.props.PropAssetResolver.PropAssetView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 会话级道具可用性缓存：一节课只解析一次，供 Tutor 提示词闸门与 SSE 舞台事件共用。
 *
 * @author liudy
 */
@Component
public class SessionPropContext {

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final int maxEntries;

    public SessionPropContext(@Value("${kidora.cet.props.session-cache-ttl-seconds:3600}") long ttlSeconds,
                              @Value("${kidora.cet.props.session-cache-max:5000}") int maxEntries) {
        this.ttlNanos = Math.max(1L, ttlSeconds) * 1_000_000_000L;
        this.maxEntries = Math.max(1, maxEntries);
    }

    /**
     * 取本课可用道具，缺失或过期时用 loader 重新解析。
     *
     * @param lessonSessionId 课时会话
     * @param loader          解析器（只在未命中时调用）
     * @return 可用道具；loader 异常由调用方处理
     */
    public List<PropAssetView> get(String lessonSessionId, Supplier<List<PropAssetView>> loader) {
        if (!StringUtils.hasText(lessonSessionId)) {
            return loader.get();
        }
        Entry hit = cache.get(lessonSessionId);
        if (hit != null && hit.isFresh(ttlNanos)) {
            return hit.assets();
        }
        List<PropAssetView> loaded = loader.get();
        purgeIfNeeded();
        cache.put(lessonSessionId, new Entry(System.nanoTime(), List.copyOf(loaded)));
        return loaded;
    }

    /**
     * 结课 / 中止时释放。
     *
     * @param lessonSessionId 课时会话
     */
    public void evict(String lessonSessionId) {
        if (StringUtils.hasText(lessonSessionId)) {
            cache.remove(lessonSessionId);
        }
    }

    private void purgeIfNeeded() {
        if (cache.size() < maxEntries) {
            return;
        }
        cache.entrySet().removeIf(e -> !e.getValue().isFresh(ttlNanos));
        if (cache.size() >= maxEntries) {
            cache.clear();
        }
    }

    private record Entry(long loadedAtNanos, List<PropAssetView> assets) {

        boolean isFresh(long ttlNanos) {
            return System.nanoTime() - loadedAtNanos < ttlNanos;
        }
    }
}
