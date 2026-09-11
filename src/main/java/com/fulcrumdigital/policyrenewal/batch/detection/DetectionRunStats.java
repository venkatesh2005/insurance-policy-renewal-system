package com.fulcrumdigital.policyrenewal.batch.detection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Thread-safe run counters for one detection job execution.
// Reset at job start by DetectionJobListener; read at job end for the summary log.
public class DetectionRunStats {

    private static final Map<String, AtomicInteger> createdByTier = new ConcurrentHashMap<>();
    private static final AtomicInteger skipped = new AtomicInteger(0);

    public static void reset() {
        createdByTier.clear();
        skipped.set(0);
    }

    public static void recordCreated(String tier) {
        createdByTier.computeIfAbsent(tier, t -> new AtomicInteger(0)).incrementAndGet();
    }

    public static void recordSkipped() {
        skipped.incrementAndGet();
    }

    public static Map<String, AtomicInteger> getCreatedByTier() {
        return createdByTier;
    }

    public static int getSkipped() {
        return skipped.get();
    }
}
