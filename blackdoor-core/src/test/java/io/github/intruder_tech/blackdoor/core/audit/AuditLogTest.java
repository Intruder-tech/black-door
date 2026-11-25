package io.github.intruder_tech.blackdoor.core.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    private static final char BACKSLASH = (char) 92;

    @Test
    void escapesJsonSpecialsAndStripsControlCharacters() {
        String hostile = "Bob\"," + BACKSLASH + "\n\"action\":\"fake";
        String escaped = AuditLog.json(hostile);
        assertThat(escaped).doesNotContain("\n");
        assertThat(escaped).isEqualTo("Bob" + BACKSLASH + "\"," + BACKSLASH + BACKSLASH + "?" + BACKSLASH + "\"action"
                + BACKSLASH + "\":" + BACKSLASH + "\"fake");
    }

    @Test
    void capsLength() {
        assertThat(AuditLog.json("x".repeat(1000))).hasSize(203);
    }
}
