package io.github.intruder_tech.blackdoor.violations;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ViolationLedgerTest {

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final BdLogger sink = new BdLogger() {
        @Override
        public void debug(String message) {
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            lines.add(message);
        }

        @Override
        public void error(String message, Throwable error) {
        }
    };
    private final ManualTimeSource time = new ManualTimeSource();

    private ViolationLedger ledger(int maxSubjects) {
        return new ViolationLedger(new RateLimitedLogger(sink, time, 2, 1, 64), maxSubjects, 5);
    }

    private static Finding finding(String check, Subject subject) {
        return new Finding(check, subject, Confidence.DETERMINISTIC, "pitch=95.0", 0L, false);
    }

    @Test
    void countsPerCheckAndSubject() {
        ViolationLedger ledger = ledger(10);
        Subject steve = new Subject(UUID.randomUUID(), "Steve");
        ledger.accept(finding("packet.invalid-rotation", steve));
        ledger.accept(finding("packet.invalid-rotation", steve));
        ledger.accept(finding("packet.invalid-slot", steve));
        assertThat(ledger.total()).isEqualTo(3);
        assertThat(ledger.countsByCheck()).containsEntry("packet.invalid-rotation", 2L).containsEntry("packet.invalid-slot", 1L);
        assertThat(ledger.countsForSubject(steve)).containsEntry("packet.invalid-rotation", 2L);
    }

    @Test
    void alertsAreRateLimitedPerCheck() {
        ViolationLedger ledger = ledger(10);
        for (int i = 0; i < 50; i++) {
            ledger.accept(finding("packet.flood", Subject.UNKNOWN));
        }
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("packet.flood").contains("(monitor)");
    }

    @Test
    void subjectsAndRecentHistoryAreBounded() {
        ViolationLedger ledger = ledger(3);
        for (int i = 0; i < 100; i++) {
            ledger.accept(finding("packet.invalid-slot", new Subject(UUID.randomUUID(), "p" + i)));
        }
        assertThat(ledger.trackedSubjects()).isEqualTo(3);
        assertThat(ledger.recent(100)).hasSize(5);
        assertThat(ledger.recent(100).get(4).subject().name()).isEqualTo("p99");
    }

    @Test
    void numberOfDistinctChecksIsBounded() {
        ViolationLedger ledger = ledger(10);
        for (int i = 0; i < ViolationLedger.MAX_CHECKS + 50; i++) {
            ledger.accept(finding("check-" + i, Subject.UNKNOWN));
        }
        assertThat(ledger.countsByCheck()).hasSize(ViolationLedger.MAX_CHECKS);
    }

    @Test
    void sanitizesAlertText() {
        ViolationLedger ledger = ledger(10);
        ledger.accept(new Finding("packet.x", new Subject(null, "Evil" + (char) 10 + "Line"), Confidence.DETERMINISTIC,
                "detail" + (char) 27 + "[2J", 0L, false));
        assertThat(lines.get(0)).doesNotContain(String.valueOf((char) 10)).doesNotContain(String.valueOf((char) 27));
    }
}
