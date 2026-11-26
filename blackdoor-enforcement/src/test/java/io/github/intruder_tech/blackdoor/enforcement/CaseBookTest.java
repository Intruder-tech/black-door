package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.violations.Confidence;
import io.github.intruder_tech.blackdoor.violations.Finding;
import io.github.intruder_tech.blackdoor.violations.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseBookTest {

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();
    private final UUID id = UUID.randomUUID();

    private CaseBook book() {
        return new CaseBook(dir.resolve("cases.tsv"), time);
    }

    private Finding finding(String detail) {
        return new Finding("packet.sign", new Subject(id, "tester"), Confidence.DETERMINISTIC, detail, 7, true);
    }

    @Test
    void keepsWhatLedToTheActionNewestFirst() {
        CaseBook book = book();
        book.open(id, "tester", CaseBook.Action.KICK, List.of(finding("line too long")));
        CaseBook.Case second = book.open(id, "tester", CaseBook.Action.BAN, List.of(finding("again")));
        assertThat(second.id()).isEqualTo(2);
        assertThat(book.newest(10)).extracting(CaseBook.Case::id).containsExactly(2, 1);
        assertThat(book.find(1)).hasValueSatisfying(c -> assertThat(c.evidence())
                .containsExactly(new CaseBook.Evidence(7, "packet.sign", "line too long", true)));
    }

    @Test
    void repeatedWouldKicksForOnePlayerMakeOneCaseForAWhile() {
        CaseBook book = book();
        book.open(id, "tester", CaseBook.Action.WOULD_KICK, List.of());
        assertThat(book.open(id, "tester", CaseBook.Action.WOULD_KICK, List.of()).id()).isEqualTo(1);
        assertThat(book.open(UUID.randomUUID(), "other", CaseBook.Action.WOULD_KICK, List.of()).id()).isEqualTo(2);
        time.advance(java.time.Duration.ofMinutes(11));
        assertThat(book.open(id, "tester", CaseBook.Action.WOULD_KICK, List.of()).id()).isEqualTo(3);
    }

    @Test
    void canBeMarkedAsAFalseAlarmAndBack() {
        CaseBook book = book();
        book.open(id, "tester", CaseBook.Action.KICK, List.of());
        assertThat(book.markFalseAlarm(1, true)).map(CaseBook.Case::falseAlarm).contains(true);
        assertThat(book.markFalseAlarm(1, false)).map(CaseBook.Case::falseAlarm).contains(false);
        assertThat(book.markFalseAlarm(99, true)).isEmpty();
    }

    @Test
    void keepsOnlyTheNewestCases() {
        CaseBook book = book();
        for (int i = 0; i < CaseBook.MAX_CASES + 5; i++) {
            book.open(id, "tester", CaseBook.Action.KICK, List.of());
        }
        assertThat(book.newest(1000)).hasSize(CaseBook.MAX_CASES);
        assertThat(book.find(5)).isEmpty();
        assertThat(book.find(6)).isPresent();
    }

    @Test
    void survivesARestartAndKeepsNumbering() throws IOException {
        CaseBook first = book();
        first.open(id, "tester", CaseBook.Action.WOULD_KICK, List.of(finding("tab\there\nand newline")));
        first.markFalseAlarm(1, true);
        first.save();
        CaseBook second = book();
        assertThat(second.unreadableLines()).isZero();
        assertThat(second.find(1)).hasValueSatisfying(c -> {
            assertThat(c.name()).isEqualTo("tester");
            assertThat(c.action()).isEqualTo(CaseBook.Action.WOULD_KICK);
            assertThat(c.falseAlarm()).isTrue();
            assertThat(c.evidence()).singleElement()
                    .satisfies(e -> assertThat(e.detail()).doesNotContain("\t", "\n"));
        });
        assertThat(second.open(id, "tester", CaseBook.Action.KICK, List.of()).id()).isEqualTo(2);
    }

    @Test
    void damagedLinesAreSkippedAndCounted() throws IOException {
        Files.writeString(dir.resolve("cases.tsv"), "junk\ncase\tx\ny\nevidence\t42\t1\tpacket.sign\ttrue\torphan\n");
        CaseBook book = book();
        assertThat(book.newest(10)).isEmpty();
        assertThat(book.unreadableLines()).isEqualTo(4);
    }
}
