package io.github.intruder_tech.blackdoor.physics.replay;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceTest {

    @Test
    void groupsEventsAndMovementByClientTick() throws Exception {
        Trace trace = Trace.read(new StringReader(String.join("\n",
                "{\"k\":\"meta\",\"v\":2}",
                "{\"t\":1,\"k\":\"move\",\"y\":64.0,\"ground\":true}",
                "{\"t\":2,\"k\":\"tick\"}",
                "{\"t\":3,\"k\":\"tick\"}",
                "{\"t\":4,\"k\":\"velocity\",\"y\":-1.5E-2}",
                "{\"t\":5,\"k\":\"move\",\"sections\":[[0,[4096,1]]],\"name\":\"a\"}",
                "{\"t\":6,\"k\":\"tick\"}")));
        assertThat(trace.meta()).containsEntry("v", 2.0);
        assertThat(trace.ticks()).hasSize(3);
        assertThat(trace.ticks().get(0).move()).containsEntry("y", 64.0).containsEntry("ground", true);
        assertThat(trace.ticks().get(1).move()).isNull();
        assertThat(trace.ticks().get(2).events()).hasSize(1);
        assertThat(trace.ticks().get(2).events().get(0)).containsEntry("y", -0.015);
        assertThat(trace.ticks().get(2).move()).containsEntry("name", "a");
    }

    @Test
    void aBrokenLineIsReportedWithItsNumber() {
        assertThatThrownBy(() -> Trace.read(new StringReader("{\"k\":\"meta\"}\n{\"k\":\"mo")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 2");
    }
}
