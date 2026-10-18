package com.fluxcraft.MiaoMenu.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按玩家计数的滑动窗口限流器。
 *
 * <p>实现上为每个玩家保存窗口内的事件时间戳队列；窗口长度与配额都可配置。
 * 使用 {@link System#nanoTime()} 作为时间源，避免系统时钟回拨导致窗口
 * 永不重置。</p>
 */
public final class RateLimiter {
    private static final int CLEANUP_INTERVAL = 256;

    private final long windowNanos;
    private final int maxEvents;
    private final Map<UUID, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final AtomicInteger accessCount = new AtomicInteger();

    public RateLimiter(Duration window, int maxEvents) {
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Rate limit window must be positive");
        }
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("Rate limit maxEvents must be positive");
        }
        this.windowNanos = window.toNanos();
        this.maxEvents = maxEvents;
    }

    public boolean allow(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        long now = System.nanoTime();
        maybeCleanup(now);

        AtomicBoolean allowed = new AtomicBoolean(false);
        windows.compute(uuid, (_, current) -> {
            Deque<Long> timestamps = current != null ? current : new ArrayDeque<>(maxEvents);
            evictExpired(timestamps, now);
            if (timestamps.size() >= maxEvents) {
                return timestamps;
            }
            timestamps.addLast(now);
            allowed.set(true);
            return timestamps;
        });
        return allowed.get();
    }

    private void evictExpired(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowNanos) {
            timestamps.pollFirst();
        }
    }

    public void clear(UUID uuid) {
        if (uuid != null) {
            windows.remove(uuid);
        }
    }

    public void clearAll() {
        windows.clear();
    }

    /** 当前被跟踪的玩家数量（用于测试与诊断）。 */
    public int trackedWindows() {
        return windows.size();
    }

    private void maybeCleanup(long now) {
        if (accessCount.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        // 通过 computeIfPresent 回收，保证与 allow() 对同一 key 的修改互斥。
        for (UUID uuid : windows.keySet()) {
            windows.computeIfPresent(uuid, (_, timestamps) -> {
                evictExpired(timestamps, now);
                return timestamps.isEmpty() ? null : timestamps;
            });
        }
    }
}
