package io.github.intruder_tech.blackdoor.enforcement;

import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static io.github.intruder_tech.blackdoor.enforcement.EnforcementSettings.Mode.KICK;
import static org.assertj.core.api.Assertions.assertThat;

class BanBookTest {

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();
    private final UUID id = UUID.randomUUID();
    /** Third kick within 24 hours bans for 15 minutes. */
    private final EnforcementSettings rules = new EnforcementSettings(KICK, 5, 60, 3, 15, 24);

    private BanBook book() {
        return new BanBook(dir.resolve("bans.tsv"), time);
    }

    @Test
    void timeLeftIsWrittenInPlainWords() {
        assertThat(BanBook.words(1)).isEqualTo("1 minute");
        assertThat(BanBook.words(Duration.ofSeconds(899).toMillis())).isEqualTo("15 minutes");
        assertThat(BanBook.words(Duration.ofMinutes(60).toMillis())).isEqualTo("1 hour");
        assertThat(BanBook.words(Duration.ofMinutes(90).toMillis())).isEqualTo("90 minutes");
        assertThat(BanBook.words(Duration.ofMinutes(1439).toMillis())).isEqualTo("24 hours");
        assertThat(BanBook.words(Duration.ofDays(1).toMillis())).isEqualTo("1 day");
        assertThat(BanBook.words(Duration.ofDays(30).toMillis())).isEqualTo("30 days");
    }

    @Test
    void theThirdKickBans() {
        BanBook book = book();
        assertThat(book.recordKick(id, "tester", rules)).isEmpty();
        assertThat(book.recordKick(id, "tester", rules)).isEmpty();
        assertThat(book.nextKickBans(id, rules)).isTrue();
        assertThat(book.recordKick(id, "tester", rules)).hasValueSatisfying(ban -> {
            assertThat(ban.name()).isEqualTo("tester");
            assertThat(ban.untilMillis()).isEqualTo(time.currentTimeMillis() + Duration.ofMinutes(15).toMillis());
        });
        assertThat(book.active(id)).isPresent();
    }

    @Test
    void banEndsByItself() {
        BanBook book = book();
        for (int i = 0; i < 3; i++) {
            book.recordKick(id, "tester", rules);
        }
        time.advance(Duration.ofMinutes(16));
        assertThat(book.active(id)).isEmpty();
        assertThat(book.active()).isEmpty();
    }

    @Test
    void oldKicksAreForgotten() {
        BanBook book = book();
        book.recordKick(id, "tester", rules);
        book.recordKick(id, "tester", rules);
        time.advance(Duration.ofHours(25));
        assertThat(book.nextKickBans(id, rules)).isFalse();
        assertThat(book.recordKick(id, "tester", rules)).isEmpty();
    }

    @Test
    void zeroMinutesNeverBans() {
        EnforcementSettings off = new EnforcementSettings(KICK, 5, 60, 1, 0, 24);
        BanBook book = book();
        assertThat(book.nextKickBans(id, off)).isFalse();
        assertThat(book.recordKick(id, "tester", off)).isEmpty();
    }

    @Test
    void bansAndKicksSurviveARestart() throws IOException {
        BanBook first = book();
        UUID other = UUID.randomUUID();
        first.recordKick(other, "second", rules);
        first.recordKick(other, "second", rules);
        for (int i = 0; i < 3; i++) {
            first.recordKick(id, "tester", rules);
        }
        first.save();
        BanBook second = book();
        assertThat(second.active(id)).map(BanBook.Ban::name).contains("tester");
        assertThat(second.nextKickBans(other, rules)).isTrue();
    }

    @Test
    void liftingABanAlsoClearsTheCount() {
        BanBook book = book();
        for (int i = 0; i < 3; i++) {
            book.recordKick(id, "Tester", rules);
        }
        assertThat(book.lift("tester")).isTrue();
        assertThat(book.active(id)).isEmpty();
        assertThat(book.nextKickBans(id, rules)).isFalse();
        assertThat(book.lift("tester")).isFalse();
    }

    @Test
    void damagedLinesAreSkippedAndCounted() throws IOException {
        Files.writeString(dir.resolve("bans.tsv"), "nonsense\nban\tnot-a-uuid\t5\tx\n"
                + "ban\t" + id + "\t" + (time.currentTimeMillis() + 60_000) + "\ttester\n");
        BanBook book = book();
        assertThat(book.active(id)).isPresent();
        assertThat(book.unreadableLines()).isEqualTo(2);
    }
}
