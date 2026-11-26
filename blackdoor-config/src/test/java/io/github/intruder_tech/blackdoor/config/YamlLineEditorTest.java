package io.github.intruder_tech.blackdoor.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlLineEditorTest {

    static final String DOC = String.join("\n",
            "# header comment",
            "config-version: 1",
            "",
            "packet:",
            "  # rotation check",
            "  invalid-rotation:",
            "    mode: monitor   # inline note",
            "  flood:",
            "    mode: monitor",
            "    movement:",
            "      burst: 200",
            "mode: monitor",
            "");

    @Test
    void changesNestedValueAndKeepsComments() throws Exception {
        String out = YamlLineEditor.setScalar(DOC, "packet.invalid-rotation.mode", "mitigate");
        assertThat(out).contains("    mode: mitigate  # inline note")
                .contains("# header comment").contains("  # rotation check")
                .contains("    mode: monitor\n    movement:");
        assertThat(YamlLoader.parse("t", out)).containsEntry("mode", "monitor");
    }

    @Test
    void distinguishesSameKeyAtDifferentDepths() {
        String out = YamlLineEditor.setScalar(DOC, "mode", "enforce");
        assertThat(out).contains("\nmode: enforce").contains("    mode: monitor   # inline note");
        String deep = YamlLineEditor.setScalar(DOC, "packet.flood.movement.burst", "300");
        assertThat(deep).contains("      burst: 300");
    }

    @Test
    void appendsMissingTopLevelSectionOnlyWhenAllowed() throws Exception {
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "setup.completed", "true")).isInstanceOf(IllegalArgumentException.class);
        String out = YamlLineEditor.setScalar(DOC, "setup.completed", "true", true);
        assertThat(out).startsWith("# header comment").endsWith("setup:\n  completed: true\n");
        assertThat(YamlLoader.parse("t", out).get("setup")).isEqualTo(java.util.Map.of("completed", true));
    }

    @Test
    void insertsMissingKeyInsideExistingSectionKeepingStructure() throws Exception {
        String out = YamlLineEditor.setScalar(DOC, "packet.transaction.mode", "monitor", true);
        assertThat(out).contains("      burst: 200\n  transaction:\n    mode: monitor\nmode: monitor");
        Object packet = YamlLoader.parse("t", out).get("packet");
        assertThat(((java.util.Map<?, ?>) packet).get("transaction")).isEqualTo(java.util.Map.of("mode", "monitor"));
        assertThat(YamlLoader.parse("t", out).get("mode")).isEqualTo("monitor");
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode.sub", "x", true))
                .as("cannot nest under a scalar").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesWindowsLineEndings() {
        String crlf = DOC.replace("\n", "\r\n");
        assertThat(YamlLineEditor.setScalar(crlf, "mode", "enforce")).contains("mode: enforce\r\n");
    }

    /** As Paper writes on Windows: a CRLF header above an LF body. */
    private static final String MIXED = "# header\r\n# more header\r\nanticheat:\n  anti-xray:\n"
            + "    enabled: false\n    engine-mode: 1\nlast: 1\r\n";

    @Test
    void changingAValueKeepsEveryLinesOwnEnding() {
        assertThat(YamlLineEditor.setScalar(MIXED, "anticheat.anti-xray.enabled", "true"))
                .isEqualTo(MIXED.replace("enabled: false", "enabled: true"));
        assertThat(YamlLineEditor.setScalar(MIXED, "last", "2")).isEqualTo(MIXED.replace("last: 1", "last: 2"));
        String noFinalNewline = "a: 1\r\nb: 2\nc: 3";
        assertThat(YamlLineEditor.setScalar(noFinalNewline, "c", "4")).isEqualTo("a: 1\r\nb: 2\nc: 4");
    }

    @Test
    void insertingIntoASectionKeepsEveryLinesOwnEnding() {
        assertThat(YamlLineEditor.setScalar(MIXED, "anticheat.anti-xray.extra", "5", true))
                .isEqualTo(MIXED.replace("    engine-mode: 1\n", "    engine-mode: 1\n    extra: 5\n"));
    }

    @Test
    void appendingAtTheEndKeepsEveryLinesOwnEnding() {
        assertThat(YamlLineEditor.setScalar(MIXED, "setup.completed", "true", true))
                .isEqualTo(MIXED + "setup:\r\n  completed: true\r\n");
        assertThat(YamlLineEditor.setScalar("a: 1\r\nb: 2", "c", "3", true)).isEqualTo("a: 1\r\nb: 2\r\nc: 3\r\n");
        assertThat(YamlLineEditor.setScalar("a:\r\n  b: 2", "a.c", "3", true)).isEqualTo("a:\r\n  b: 2\r\n  c: 3");
    }

    @Test
    void rejectsMissingKeysSectionsAndUnsafeValues() {
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "packet.nope.mode", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "packet.flood", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode", "a: b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode", "x # y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
