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

    @Test
    void rejectsMissingKeysSectionsAndUnsafeValues() {
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "packet.nope.mode", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "packet.flood", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode", "a: b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode", "x # y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YamlLineEditor.setScalar(DOC, "mode", "")).isInstanceOf(IllegalArgumentException.class);
    }
}
