package io.github.intruder_tech.blackdoor.core.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTest {

    private static final char S = (char) 0x00A7;

    @Test
    void translatesColourCodes() {
        assertThat(Text.toLegacy("&aok &Lbold")).isEqualTo(S + "aok " + S + "lbold");
    }

    @Test
    void escapedTextCannotInjectCodes() {
        String hostile = "&cfake " + S + "kmagic";
        String rendered = Text.toLegacy("&7" + Text.escape(hostile));
        assertThat(rendered).isEqualTo(S + "7&cfake ?kmagic");
    }

    @Test
    void leavesUnknownCodesAlone() {
        assertThat(Text.toLegacy("&zx & y")).isEqualTo("&zx & y");
    }
}
