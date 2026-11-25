package io.github.intruder_tech.blackdoor.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlLoaderTest {

    @Test
    void parsesNestedMappings() throws Exception {
        Map<String, Object> root = YamlLoader.parse("t.yml", "a:\n  b: 1\nc: text\n");
        assertThat(root).containsEntry("c", "text");
        assertThat(root.get("a")).isEqualTo(Map.of("b", 1));
    }

    @Test
    void emptyDocumentIsEmptyMap() throws Exception {
        assertThat(YamlLoader.parse("t.yml", "")).isEmpty();
    }

    @Test
    void rejectsNonMappingRoot() {
        assertThatThrownBy(() -> YamlLoader.parse("t.yml", "- a\n- b\n"))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("root must be a mapping");
    }

    @Test
    void rejectsAliasBomb() {
        StringBuilder yaml = new StringBuilder("a: &a [x, x, x, x, x, x, x, x, x]\n");
        String previous = "a";
        for (int level = 0; level < 10; level++) {
            String name = "l" + level;
            yaml.append(name).append(": &").append(name).append(" [");
            for (int i = 0; i < 9; i++) {
                yaml.append(i == 0 ? "" : ", ").append('*').append(previous);
            }
            yaml.append("]\n");
            previous = name;
        }
        assertThatThrownBy(() -> YamlLoader.parse("bomb.yml", yaml.toString()))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("invalid YAML");
    }

    @Test
    void rejectsArbitraryTypeConstruction() {
        String yaml = "x: !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL [\"http://127.0.0.1/\"]]]]\n";
        assertThatThrownBy(() -> YamlLoader.parse("evil.yml", yaml))
                .isInstanceOf(ConfigLoadException.class)
                .hasMessageContaining("invalid YAML");
    }

    @Test
    void rejectsDuplicateKeys() {
        assertThatThrownBy(() -> YamlLoader.parse("dup.yml", "a: 1\na: 2\n"))
                .isInstanceOf(ConfigLoadException.class);
    }

    @Test
    void rejectsExcessiveNesting() {
        StringBuilder yaml = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            yaml.append("  ".repeat(i)).append("k").append(i).append(":\n");
        }
        yaml.append("  ".repeat(100)).append("v: 1\n");
        assertThatThrownBy(() -> YamlLoader.parse("deep.yml", yaml.toString()))
                .isInstanceOf(ConfigLoadException.class);
    }

    @Test
    void dumpRoundTrips() throws Exception {
        Map<String, Object> root = YamlLoader.parse("t.yml", "a:\n  b: 1\nlist:\n- x\n- y\n");
        assertThat(YamlLoader.parse("t.yml", YamlLoader.dump(root))).isEqualTo(root);
    }
}
