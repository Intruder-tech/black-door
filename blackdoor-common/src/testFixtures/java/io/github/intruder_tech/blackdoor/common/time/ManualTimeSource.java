package io.github.intruder_tech.blackdoor.common.time;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** Test clock advanced explicitly by the test. */
public final class ManualTimeSource implements TimeSource {

    private final AtomicLong nanos = new AtomicLong();
    private final AtomicLong millis = new AtomicLong(1_700_000_000_000L);

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    @Override
    public long currentTimeMillis() {
        return millis.get();
    }

    public void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
        millis.addAndGet(duration.toMillis());
    }
}
