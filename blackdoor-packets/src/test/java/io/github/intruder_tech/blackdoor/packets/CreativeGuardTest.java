package io.github.intruder_tech.blackdoor.packets;

import io.github.intruder_tech.blackdoor.common.log.RateLimitedLogger;
import io.github.intruder_tech.blackdoor.common.time.ManualTimeSource;
import io.github.intruder_tech.blackdoor.protocol.packet.ClientboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionInfo;
import io.github.intruder_tech.blackdoor.protocol.packet.ConnectionState;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket;
import io.github.intruder_tech.blackdoor.protocol.packet.ServerboundPacket.CreativeItem;
import io.github.intruder_tech.blackdoor.protocol.packet.StructureSize;
import io.github.intruder_tech.blackdoor.state.SessionRegistry;
import io.github.intruder_tech.blackdoor.violations.Finding;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Creative-mode guards: item data shape, and creative actions from players not in creative. */
class CreativeGuardTest {

    private final ManualTimeSource time = new ManualTimeSource();
    private final List<Finding> findings = new CopyOnWriteArrayList<>();
    private final ConnectionInfo conn = new ConnectionInfo(new Object(), UUID.randomUUID(), "Steve",
            PacketSecurityEngineTest.V);
    private final PacketSecurityEngine engine = new PacketSecurityEngine(new SessionRegistry(10, time), findings::add,
            time, PacketSettings::defaults, () -> false, PacketSecurityEngineTest.V,
            new RateLimitedLogger(PacketSecurityEngineTest.SILENT, time, 5, 1, 8));

    private void creativeAction(int depth, int elements) {
        engine.onInbound(conn, ConnectionState.PLAY, 100, new CreativeItem(10, 1, depth, elements));
    }

    private void gameMode(boolean creative) {
        // The session is created by the first inbound packet.
        engine.onInbound(conn, ConnectionState.PLAY, 10, new ServerboundPacket.ArmSwing());
        engine.onOutbound(conn, new ClientboundPacket.GameModeChange(creative));
    }

    private static Iterable<?> childrenOf(Object node) {
        return node instanceof List<?> list ? list : List.of();
    }

    private List<String> ids() {
        return findings.stream().map(Finding::checkId).toList();
    }

    @Test
    void itemDataAtTheLimitsPasses() {
        creativeAction(32, 8192);
        assertThat(findings).isEmpty();
    }

    @Test
    void flagsItemDataTooDeepOrTooLarge() {
        creativeAction(33, 10);
        creativeAction(2, 8193);
        assertThat(ids()).containsExactly("packet.item-data", "packet.item-data");
    }

    @Test
    void unknownGameModeIsNotEvidence() {
        creativeAction(1, 1);
        assertThat(findings).isEmpty();
    }

    @Test
    void creativePlayersMayUseCreativeActions() {
        gameMode(true);
        creativeAction(1, 1);
        assertThat(findings).isEmpty();
    }

    @Test
    void flagsCreativeActionOutsideCreative() {
        gameMode(false);
        creativeAction(1, 1);
        assertThat(ids()).containsExactly("packet.game-mode");
    }

    @Test
    void actionsStillInFlightAfterLeavingCreativeAreTolerated() {
        gameMode(true);
        gameMode(false);
        time.advance(Duration.ofSeconds(4));
        creativeAction(1, 1);
        assertThat(findings).isEmpty();

        time.advance(Duration.ofSeconds(2));
        creativeAction(1, 1);
        assertThat(ids()).containsExactly("packet.game-mode");
    }

    @Test
    void repeatedNonCreativeUpdatesDoNotExtendTheTolerance() {
        gameMode(true);
        gameMode(false);
        time.advance(Duration.ofSeconds(6));
        gameMode(false);
        creativeAction(1, 1);
        assertThat(ids()).containsExactly("packet.game-mode");
    }

    @Test
    void measuresDepthAndElements() {
        List<Object> tree = List.of("a", List.of("b", List.of("c")));
        StructureSize size = StructureSize.of((Object) tree, CreativeGuardTest::childrenOf);
        assertThat(size.depth()).isEqualTo(4);
        assertThat(size.elements()).isEqualTo(6);
    }

    @Test
    void measuringHostileDataIsBoundedAndDoesNotOverflowTheStack() {
        List<Object> node = new ArrayList<>();
        for (int i = 0; i < 200_000; i++) {
            node = new ArrayList<>(List.of(node));
        }
        StructureSize size = StructureSize.of((Object) node, CreativeGuardTest::childrenOf);
        assertThat(size.depth()).isEqualTo(StructureSize.CAP);
        assertThat(size.elements()).isEqualTo(StructureSize.CAP);
    }
}
