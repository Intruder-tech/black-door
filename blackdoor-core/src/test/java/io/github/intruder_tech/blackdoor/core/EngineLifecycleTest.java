package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.api.BlackDoorProvider;
import io.github.intruder_tech.blackdoor.common.concurrent.BoundedExecutor;
import io.github.intruder_tech.blackdoor.common.time.TimeSource;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineLifecycleTest {

    @TempDir
    Path dir;

    private BlackDoorEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.disable();
        }
    }

    @Test
    void enableCreatesDefaultsRegistersApiAndRuns() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
        assertThat(engine.state()).isEqualTo(EngineState.RUNNING);
        assertThat(dir.resolve("config.yml")).exists();
        assertThat(BlackDoorProvider.get().pluginVersion()).isEqualTo("0.0.0-test");
        assertThat(engine.settings().mode().name()).isEqualTo("MONITOR");
    }

    @Test
    void announcesInitializationBeforeAnythingElse() {
        FakePlatform platform = FakePlatform.paper(dir);
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        assertThat(platform.log.get(0)).startsWith("INFO Initializing Black Door 0.0.0-test on PAPER");
    }

    @Test
    void disableStopsThreadsUnregistersApiAndCancelsTasks() throws Exception {
        FakePlatform platform = FakePlatform.paper(dir);
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        engine.submitWork(() -> { });
        engine.disable();
        assertThat(engine.state()).isEqualTo(EngineState.STOPPED);
        assertThat(BlackDoorProvider.find()).isEmpty();
        assertThat(platform.cancelAllCalls.get()).isEqualTo(1);
        assertThat(blackDoorThreads()).isZero();
        engine.disable(); // idempotent
    }

    @Test
    void repeatedEnableDisableCyclesDoNotLeakThreads() {
        for (int i = 0; i < 5; i++) {
            BlackDoorEngine cycle = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
            cycle.enable();
            cycle.submitWork(() -> { });
            cycle.disable();
        }
        assertThat(blackDoorThreads()).isZero();
    }

    @Test
    void invalidConfigStartsDegradedWithDefaults() throws Exception {
        Files.writeString(dir.resolve("config.yml"), "config-version: 1\nmode: chaos\n");
        FakePlatform platform = FakePlatform.paper(dir);
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        assertThat(engine.state()).isEqualTo(EngineState.DEGRADED);
        assertThat(engine.settings().mode().name()).isEqualTo("MONITOR");
        assertThat(platform.logged("ERROR", "mode")).isTrue();
    }

    @Test
    void missingPacketLibraryIsDegradedOnBackend() {
        engine = new BlackDoorEngine(new FakePlatform(dir, PlatformType.SPIGOT, MinecraftVersion.of(1, 8, 8), false),
                TimeSource.system());
        engine.enable();
        assertThat(engine.state()).isEqualTo(EngineState.DEGRADED);
        assertThat(engine.degradedReasons()).anyMatch(r -> r.contains("packet library unavailable"));
    }

    @Test
    void proxyDoesNotNeedPacketLibrary() {
        engine = new BlackDoorEngine(new FakePlatform(dir, PlatformType.VELOCITY, null, false), TimeSource.system());
        engine.enable();
        assertThat(engine.state()).isEqualTo(EngineState.RUNNING);
        assertThat(engine.commands().label()).isEqualTo(BlackDoorEngine.PROXY_LABEL);
    }

    @Test
    void warnsAboutUnknownNewerMinecraftVersion() {
        FakePlatform platform = new FakePlatform(dir, PlatformType.PAPER, MinecraftVersion.of(27, 1), true);
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        assertThat(platform.logged("WARN", "newer than this build knows")).isTrue();
    }

    @Test
    void enforceModeWarnsThatNothingIsEnforced() throws Exception {
        Files.writeString(dir.resolve("config.yml"), "config-version: 1\nmode: enforce\n");
        FakePlatform platform = FakePlatform.paper(dir);
        engine = new BlackDoorEngine(platform, TimeSource.system());
        engine.enable();
        assertThat(platform.logged("WARN", "will block offending packets")).isTrue();
    }

    @Test
    void cannotEnableTwice() {
        engine = new BlackDoorEngine(FakePlatform.paper(dir), TimeSource.system());
        engine.enable();
        assertThatThrownBy(engine::enable).isInstanceOf(IllegalStateException.class);
    }

    private static long blackDoorThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(t -> t.getName().startsWith(BoundedExecutor.THREAD_PREFIX))
                .count();
    }
}
