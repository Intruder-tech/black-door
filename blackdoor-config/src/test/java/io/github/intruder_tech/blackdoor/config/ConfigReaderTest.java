package io.github.intruder_tech.blackdoor.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigReaderTest {

    enum Profile { SURVIVAL, COMPETITIVE_PVP }

    private ConfigReader reader(String yaml) throws Exception {
        Map<String, Object> root = YamlLoader.parse("t.yml", yaml);
        return new ConfigReader("t.yml", root);
    }

    @Test
    void missingKeysUseDefaultsWithoutErrors() throws Exception {
        ConfigReader r = reader("");
        assertThat(r.getInt("a.b", 7, 0, 10)).isEqualTo(7);
        assertThat(r.getBoolean("flag", true)).isTrue();
        assertThat(r.errors()).isEmpty();
    }

    @Test
    void collectsEveryErrorWithPath() throws Exception {
        ConfigReader r = reader("a:\n  b: 99\nflag: maybe\nname: 5\n");
        r.getInt("a.b", 7, 0, 10);
        r.getBoolean("flag", false);
        r.getString("name", "x", 10, null);
        assertThat(r.errors()).extracting(ConfigError::path).containsExactly("a.b", "flag", "name");
    }

    @Test
    void rejectsHugeNumbersWithoutOverflow() throws Exception {
        ConfigReader r = reader("n: 99999999999999999999999\n");
        assertThat(r.getInt("n", 1, 0, 10)).isEqualTo(1);
        assertThat(r.errors()).hasSize(1);
    }

    @Test
    void readsKebabCaseEnums() throws Exception {
        ConfigReader r = reader("profile: competitive-pvp\nbad: nope\n");
        assertThat(r.getEnum("profile", Profile.class, Profile.SURVIVAL)).isEqualTo(Profile.COMPETITIVE_PVP);
        assertThat(r.getEnum("bad", Profile.class, Profile.SURVIVAL)).isEqualTo(Profile.SURVIVAL);
        assertThat(r.errors()).singleElement().satisfies(e -> assertThat(e.message()).contains("competitive-pvp"));
    }

    @Test
    void validatesStringPatternAndLength() throws Exception {
        ConfigReader r = reader("lang: en_US\nbad: '../../etc'\nlong: abcdefghijk\n");
        Pattern lang = Pattern.compile("[a-z]{2}_[A-Z]{2}");
        assertThat(r.getString("lang", "x", 5, lang)).isEqualTo("en_US");
        r.getString("bad", "x", 20, lang);
        r.getString("long", "x", 5, null);
        assertThat(r.errors()).hasSize(2);
    }

    @Test
    void reportsUnknownKeysAsWarnings() throws Exception {
        ConfigReader r = reader("known: 1\nsection:\n  known: 2\n  typo: 3\nother: 4\n");
        r.getInt("known", 0, 0, 10);
        r.getInt("section.known", 0, 0, 10);
        r.reportUnknownKeys();
        assertThat(r.errors()).isEmpty();
        assertThat(r.warnings()).extracting(ConfigError::path).containsExactlyInAnyOrder("section.typo", "other");
    }
}
