package io.github.intruder_tech.blackdoor.common.log;

import io.github.intruder_tech.blackdoor.common.ratelimit.TokenBucket;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-category rate limiting for log output (SR-110). Each category gets its own token bucket;
 * suppressed messages are counted and reported when the category is allowed to log again.
 *
 * <p>The number of tracked categories is bounded; the least recently used category is evicted.
 */
public final class RateLimitedLogger {

    private final BdLogger delegate;
    private final TimeSource time;
    private final int burst;
    private final double perSecond;
    private final Map<String, Category> categories;

    public RateLimitedLogger(BdLogger delegate, TimeSource time, int burst, double perSecond, int maxCategories) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.time = Objects.requireNonNull(time, "time");
        if (burst < 1 || maxCategories < 1) {
            throw new IllegalArgumentException("burst and maxCategories must be >= 1");
        }
        this.burst = burst;
        this.perSecond = perSecond;
        this.categories = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Category> eldest) {
                return size() > maxCategories;
            }
        };
    }

    public void warn(String category, String message) {
        emit(category, message, null, false);
    }

    public void error(String category, String message, Throwable error) {
        emit(category, message, error, true);
    }

    /** Number of categories currently tracked (bounded by {@code maxCategories}). */
    public synchronized int trackedCategories() {
        return categories.size();
    }

    private void emit(String category, String message, Throwable error, boolean isError) {
        long suppressed;
        synchronized (this) {
            Category entry = categories.computeIfAbsent(category, c -> new Category(new TokenBucket(burst, perSecond, time)));
            if (!entry.bucket.tryAcquire()) {
                entry.suppressed++;
                return;
            }
            suppressed = entry.suppressed;
            entry.suppressed = 0;
        }
        String text = suppressed > 0 ? message + " (" + suppressed + " similar messages suppressed)" : message;
        if (isError) {
            delegate.error(text, error);
        } else {
            delegate.warn(text);
        }
    }

    private static final class Category {
        final TokenBucket bucket;
        long suppressed;

        Category(TokenBucket bucket) {
            this.bucket = bucket;
        }
    }
}
