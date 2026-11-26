package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuarantineBookTest {

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();
    private final UUID id = UUID.randomUUID();

    private QuarantineBook book() {
        return new QuarantineBook(dir.resolve("quarantine.tsv"), time);
    }

    @Test
    void endsByItselfAfterTheGivenMinutes() {
        QuarantineBook book = book();
        assertThat(book.add(id, "tester", 15)).isTrue();
        assertThat(book.add(id, "tester", 15)).as("already held").isFalse();
        assertThat(book.holds(id)).isTrue();
        time.advance(Duration.ofMinutes(16));
        assertThat(book.holds(id)).isFalse();
        assertThat(book.active()).isEmpty();
    }

    @Test
    void zeroMinutesLastsUntilStaffRelease() {
        QuarantineBook book = book();
        book.add(id, "Tester", 0);
        time.advance(Duration.ofDays(400));
        assertThat(book.holds(id)).isTrue();
        assertThat(book.release("tester")).isTrue();
        assertThat(book.holds(id)).isFalse();
        assertThat(book.release("tester")).isFalse();
    }

    @Test
    void survivesARestartAndSkipsDamagedLines() throws IOException {
        QuarantineBook first = book();
        first.add(id, "tester", 60);
        first.save();
        Files.writeString(dir.resolve("quarantine.tsv"), "junk\n", java.nio.file.StandardOpenOption.APPEND);
        QuarantineBook second = book();
        assertThat(second.holds(id)).isTrue();
        assertThat(second.active()).extracting(QuarantineBook.Held::name).containsExactly("tester");
        assertThat(second.unreadableLines()).isEqualTo(1);
    }
}
