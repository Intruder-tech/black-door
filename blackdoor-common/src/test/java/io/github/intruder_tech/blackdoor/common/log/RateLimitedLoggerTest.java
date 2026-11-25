package io.github.intruder_tech.blackdoor.common.log;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitedLoggerTest {

    private final ManualTimeSource time = new ManualTimeSource();
    private final RecordingLogger sink = new RecordingLogger();

    @Test
    void suppressesBurstsAndReportsCount() {
        RateLimitedLogger logger = new RateLimitedLogger(sink, time, 2, 1, 16);
        for (int i = 0; i < 10; i++) {
            logger.warn("flood", "msg");
        }
        assertThat(sink.lines).containsExactly("WARN msg", "WARN msg");
        time.advance(Duration.ofSeconds(1));
        logger.warn("flood", "msg");
        assertThat(sink.lines).last().isEqualTo("WARN msg (8 similar messages suppressed)");
    }

    @Test
    void categoriesAreIndependent() {
        RateLimitedLogger logger = new RateLimitedLogger(sink, time, 1, 0, 16);
        logger.warn("a", "one");
        logger.warn("a", "two");
        logger.warn("b", "three");
        assertThat(sink.lines).containsExactly("WARN one", "WARN three");
    }

    @Test
    void trackedCategoriesAreBounded() {
        RateLimitedLogger logger = new RateLimitedLogger(sink, time, 1, 1, 8);
        for (int i = 0; i < 1000; i++) {
            logger.warn("cat-" + i, "x");
        }
        assertThat(logger.trackedCategories()).isEqualTo(8);
    }
}
