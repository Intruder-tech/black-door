package io.github.intruder_tech.blackdoor.common.ratelimit;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketTest {

    private final ManualTimeSource time = new ManualTimeSource();

    @Test
    void startsFullAndDrains() {
        TokenBucket bucket = new TokenBucket(3, 1, time);
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isFalse();
    }

    @Test
    void refillsOverTimeButNeverAboveCapacity() {
        TokenBucket bucket = new TokenBucket(2, 1, time);
        bucket.tryAcquire(2);
        time.advance(Duration.ofMillis(1500));
        assertThat(bucket.available()).isCloseTo(1.5, org.assertj.core.data.Offset.offset(1e-9));
        time.advance(Duration.ofHours(1));
        assertThat(bucket.available()).isEqualTo(2.0);
    }

    @Test
    void partialAcquireTakesNothing() {
        TokenBucket bucket = new TokenBucket(2, 0, time);
        assertThat(bucket.tryAcquire(3)).isFalse();
        assertThat(bucket.available()).isEqualTo(2.0);
    }

    @Test
    void zeroRefillNeverRecovers() {
        TokenBucket bucket = new TokenBucket(1, 0, time);
        assertThat(bucket.tryAcquire()).isTrue();
        time.advance(Duration.ofDays(1));
        assertThat(bucket.tryAcquire()).isFalse();
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> new TokenBucket(0, 1, time)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(Double.NaN, 1, time)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, -1, time)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, Double.POSITIVE_INFINITY, time)).isInstanceOf(IllegalArgumentException.class);
        TokenBucket bucket = new TokenBucket(1, 1, time);
        assertThatThrownBy(() -> bucket.tryAcquire(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucket.tryAcquire(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
