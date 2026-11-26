package io.github.intruder_tech.blackdoor.auth;

import io.github.intruder_tech.blackdoor.auth.AuthService.Result;
import io.github.intruder_tech.blackdoor.auth.AuthService.State;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    private static final String PASSWORD = "correct-horse-7";
    private static final String IP = "203.0.113.5";

    @TempDir
    Path dir;

    private final ManualTimeSource time = new ManualTimeSource();
    private final UUID id = UUID.randomUUID();
    private AuthSettings settings = new AuthSettings(true, 24, 60, 3, 5, 8);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = open();
    }

    private AuthService open() {
        // few iterations: tests check behaviour, not hashing cost
        return new AuthService(dir, time, () -> settings, new PasswordHasher(1_000), (message, error) -> { });
    }

    private void signUpAndLeave() {
        assertThat(auth.join(id, "Steve", IP)).isEqualTo(State.NEEDS_SIGNUP);
        assertThat(auth.register(id, "Steve", IP, PASSWORD, PASSWORD)).isEqualTo(Result.OK);
        auth.quit(id);
    }

    @Test
    void newPlayerIsBlockedUntilSignedUp() {
        auth.join(id, "Steve", IP);
        assertThat(auth.isBlocked(id)).isTrue();
        assertThat(auth.register(id, "Steve", IP, PASSWORD, PASSWORD)).isEqualTo(Result.OK);
        assertThat(auth.isBlocked(id)).isFalse();
    }

    @Test
    void weakOrMismatchedPasswordsAreRefused() {
        auth.join(id, "Steve", IP);
        assertThat(auth.register(id, "Steve", IP, PASSWORD, "other-pass-1")).isEqualTo(Result.MISMATCH);
        assertThat(auth.register(id, "Steve", IP, "short", "short")).isEqualTo(Result.TOO_SHORT);
        String longOne = "x".repeat(65);
        assertThat(auth.register(id, "Steve", IP, longOne, longOne)).isEqualTo(Result.TOO_LONG);
        assertThat(auth.register(id, "LongPlayerName", IP, "longplayername", "longplayername"))
                .isEqualTo(Result.SAME_AS_NAME);
        assertThat(auth.isBlocked(id)).isTrue();
    }

    @Test
    void sameAccountAndAddressSkipsPasswordForTwentyFourHours() {
        signUpAndLeave();
        time.advance(Duration.ofHours(23));
        assertThat(auth.join(id, "Steve", IP)).isEqualTo(State.AUTHENTICATED);
    }

    @Test
    void windowCountsFromLastPasswordEntryNotLastVisit() {
        signUpAndLeave();
        time.advance(Duration.ofHours(23));
        auth.join(id, "Steve", IP);
        auth.quit(id);
        time.advance(Duration.ofHours(2));
        assertThat(auth.join(id, "Steve", IP)).isEqualTo(State.NEEDS_SIGNIN);
        assertThat(auth.login(id, IP, PASSWORD)).isEqualTo(Result.OK);
        assertThat(auth.isBlocked(id)).isFalse();
    }

    @Test
    void anotherAddressNeedsThePassword() {
        signUpAndLeave();
        assertThat(auth.join(id, "Steve", "198.51.100.9")).isEqualTo(State.NEEDS_SIGNIN);
    }

    @Test
    void sessionsAndAccountsSurviveARestart() {
        signUpAndLeave();
        assertThat(open().join(id, "Steve", IP)).isEqualTo(State.AUTHENTICATED);
    }

    @Test
    void storeHoldsNeitherPasswordNorAddress() throws IOException {
        signUpAndLeave();
        String stored = Files.readString(dir.resolve("accounts.tsv"));
        assertThat(stored).contains("pbkdf2-sha256$").doesNotContain(PASSWORD).doesNotContain(IP);
    }

    @Test
    void repeatedWrongPasswordsLockThatAddressForAWhile() {
        signUpAndLeave();
        time.advance(Duration.ofHours(25));
        auth.join(id, "Steve", IP);
        assertThat(auth.login(id, IP, "wrong-pass-1")).isEqualTo(Result.WRONG_PASSWORD);
        assertThat(auth.login(id, IP, "wrong-pass-2")).isEqualTo(Result.WRONG_PASSWORD);
        assertThat(auth.login(id, IP, "wrong-pass-3")).isEqualTo(Result.LOCKED_OUT);
        auth.quit(id);
        auth.join(id, "Steve", IP);
        assertThat(auth.login(id, IP, PASSWORD)).as("still locked after rejoin").isEqualTo(Result.LOCKED_OUT);
        auth.quit(id);
        // an attacker's lockout must not lock the real owner out from elsewhere
        auth.join(id, "Steve", "198.51.100.9");
        assertThat(auth.login(id, "198.51.100.9", PASSWORD)).isEqualTo(Result.OK);
        auth.quit(id);
        time.advance(Duration.ofMinutes(6));
        auth.join(id, "Steve", IP);
        assertThat(auth.login(id, IP, PASSWORD)).isEqualTo(Result.OK);
    }

    @Test
    void aSecondAccountWithTheSameNameInOtherCaseIsRefused() {
        signUpAndLeave();
        UUID other = UUID.randomUUID();
        auth.join(other, "steve", IP);
        assertThat(auth.register(other, "steve", IP, PASSWORD, PASSWORD)).isEqualTo(Result.NAME_TAKEN);
    }

    @Test
    void switchedOffNothingIsBlocked() {
        settings = new AuthSettings(false, 24, 60, 3, 5, 8);
        assertThat(auth.join(id, "Steve", IP)).isEqualTo(State.AUTHENTICATED);
        assertThat(auth.isBlocked(id)).isFalse();
    }

    @Test
    void playersWhoNeverAuthenticateTimeOut() {
        auth.join(id, "Steve", IP);
        assertThat(auth.timedOut()).isEmpty();
        time.advance(Duration.ofSeconds(61));
        assertThat(auth.timedOut()).containsExactly(id);
    }

    @Test
    void resetRemovesTheAccount() {
        signUpAndLeave();
        assertThat(auth.reset("steve")).isTrue();
        assertThat(auth.join(id, "Steve", IP)).isEqualTo(State.NEEDS_SIGNUP);
        assertThat(auth.reset("nobody")).isFalse();
    }

    @Test
    void unreadableAccountFileKeepsPlayersLockedAndIsNotOverwritten() throws IOException {
        Files.writeString(dir.resolve("accounts.tsv"), "garbage\n");
        AuthService broken = open();
        broken.join(id, "Steve", IP);
        assertThat(broken.isBlocked(id)).isTrue();
        assertThat(broken.register(id, "Steve", IP, PASSWORD, PASSWORD)).isEqualTo(Result.STORAGE_ERROR);
        assertThat(Files.readString(dir.resolve("accounts.tsv"))).isEqualTo("garbage\n");
    }
}
