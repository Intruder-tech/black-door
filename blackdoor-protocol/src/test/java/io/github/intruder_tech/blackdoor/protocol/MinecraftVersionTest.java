package io.github.intruder_tech.blackdoor.protocol;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinecraftVersionTest {

    @ParameterizedTest
    @CsvSource({
            "1.8.8, 1, 8, 8",
            "1.21, 1, 21, 0",
            "1.21.11, 1, 21, 11",
            "26.1, 26, 1, 0",
            "26.3.1, 26, 3, 1",
            "1.20.1-R0.1-SNAPSHOT, 1, 20, 1",
            "26.1.2-R0.1-SNAPSHOT, 26, 1, 2",
            "'  1.12.2 ', 1, 12, 2",
    })
    void parsesReleaseNumbers(String text, int major, int minor, int patch) {
        assertThat(MinecraftVersion.parse(text)).contains(MinecraftVersion.of(major, minor, patch));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1", "0.8", "1.", ".1.2", "12345.1", "1.2.33333", "git-Paper-123"})
    void rejectsGarbage(String text) {
        assertThat(MinecraftVersion.parse(text)).isEmpty();
    }

    @Test
    void nullIsEmpty() {
        assertThat(MinecraftVersion.parse(null)).isEmpty();
    }

    @Test
    void yearBasedVersionsAreNewerThanLegacy() {
        assertThat(MinecraftVersion.of(26, 1)).isGreaterThan(MinecraftVersion.of(1, 21, 11));
        assertThat(MinecraftVersion.of(26, 1).isYearBased()).isTrue();
        assertThat(MinecraftVersion.of(1, 21, 11).isYearBased()).isFalse();
    }

    @Test
    void ordersNumericallyNotLexically() {
        assertThat(MinecraftVersion.of(1, 9)).isLessThan(MinecraftVersion.of(1, 10));
        assertThat(MinecraftVersion.of(1, 21, 2)).isLessThan(MinecraftVersion.of(1, 21, 10));
    }

    @Test
    void printsWithoutTrailingZeroPatch() {
        assertThat(MinecraftVersion.of(1, 21)).hasToString("1.21");
        assertThat(MinecraftVersion.of(1, 8, 8)).hasToString("1.8.8");
    }

    @Test
    void parseOrThrowRejects() {
        assertThatThrownBy(() -> MinecraftVersion.parseOrThrow("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
