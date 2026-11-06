package dev.momentum.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fixed-window request throttling for auth and AI endpoints. */
public final class RateLimiter {
    private record Window(Instant startedAt, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Duration duration;

    public RateLimiter(int limit, Duration duration) {
        this.limit = limit;
        this.duration = duration;
    }

    public boolean allow(String key) {
        Instant now = Instant.now();
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || Duration.between(existing.startedAt(), now).compareTo(duration) >= 0) {
                return new Window(now, 1);
            }
            return new Window(existing.startedAt(), existing.count() + 1);
        });
        return window != null && window.count() <= limit;
    }

    public void cleanup() {
        Instant now = Instant.now();
        windows.entrySet().removeIf(entry -> Duration.between(entry.getValue().startedAt(), now).compareTo(duration) >= 0);
    }

    public int limit() {
        return limit;
    }
}
