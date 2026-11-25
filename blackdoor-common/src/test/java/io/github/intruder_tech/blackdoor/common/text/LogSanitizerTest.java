package io.github.intruder_tech.blackdoor.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void replacesLineBreaksAndControlCharacters() {
        assertThat(LogSanitizer.sanitize("a\nb\rc\td" + (char) 27 + "[31m")).isEqualTo("a?b?c?d?[31m");
    }

    @Test
    void replacesFormattingAndBidiCharacters() {
        String input = "x" + (char) 0x00A7 + "cy" + (char) 0x202E + "z" + (char) 0x2028;
        assertThat(LogSanitizer.sanitize(input)).isEqualTo("x?cy?z?");
    }

    @Test
    void truncatesLongValues() {
        assertThat(LogSanitizer.sanitize("abcdef", 3)).isEqualTo("abc...");
    }

    @Test
    void keepsOrdinaryText() {
        assertThat(LogSanitizer.sanitize("Steve_123 used /blackdoor status")).isEqualTo("Steve_123 used /blackdoor status");
    }

    @Test
    void handlesNull() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
    }
}
