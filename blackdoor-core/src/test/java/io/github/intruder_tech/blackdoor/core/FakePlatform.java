package io.github.intruder_tech.blackdoor.core;

import io.github.intruder_tech.blackdoor.common.log.BdLogger;
import io.github.intruder_tech.blackdoor.core.platform.Platform;
import io.github.intruder_tech.blackdoor.core.platform.PlatformInfo;
import io.github.intruder_tech.blackdoor.core.platform.PlatformScheduler;
import io.github.intruder_tech.blackdoor.core.platform.PlatformType;
import io.github.intruder_tech.blackdoor.protocol.MinecraftVersion;
import io.github.intruder_tech.blackdoor.protocol.ProtocolBridge;
import io.github.intruder_tech.blackdoor.protocol.UnavailableProtocolBridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory platform for core tests. */
public final class FakePlatform implements Platform {

    public final List<String> log = new CopyOnWriteArrayList<>();
    public final AtomicInteger cancelAllCalls = new AtomicInteger();
    public final List<String> kicked = new CopyOnWriteArrayList<>();

    @Override
    public boolean kick(UUID playerId, String message) {
        return kicked.add(playerId + " " + message);
    }
    public io.github.intruder_tech.blackdoor.core.platform.SecurityFacts facts =
            io.github.intruder_tech.blackdoor.core.platform.SecurityFacts.UNKNOWN;

    /** Paper's anti-xray files, when the test provides them. */
    public io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles antiXray;

    @Override
    public io.github.intruder_tech.blackdoor.core.platform.SecurityFacts securityFacts() {
        return facts;
    }

    @Override
    public java.util.Optional<io.github.intruder_tech.blackdoor.core.antixray.AntiXrayFiles> antiXrayFiles() {
        return java.util.Optional.ofNullable(antiXray);
    }

    private final Path dataDirectory;
    private final PlatformInfo info;
    private final ProtocolBridge protocol;

    public FakePlatform(Path dataDirectory, PlatformType type, MinecraftVersion version, boolean protocolAvailable) {
        this.dataDirectory = dataDirectory;
        this.info = new PlatformInfo(type, "Fake", "1.0", type.isProxy() ? null : version);
        this.protocol = protocolAvailable ? new AvailableBridge(version) : new UnavailableProtocolBridge("test");
    }

    public static FakePlatform paper(Path dir) {
        return new FakePlatform(dir, PlatformType.PAPER, MinecraftVersion.of(1, 21, 4), true);
    }

    public boolean logged(String level, String fragment) {
        return log.stream().anyMatch(l -> l.startsWith(level) && l.contains(fragment));
    }

    @Override
    public PlatformInfo info() {
        return info;
    }

    @Override
    public PlatformScheduler scheduler() {
        return new PlatformScheduler() {
            @Override
            public void runGlobal(Runnable task) {
                task.run();
            }

            @Override
            public void runForPlayer(UUID playerId, Runnable task) {
                task.run();
            }

            @Override
            public void cancelAll() {
                cancelAllCalls.incrementAndGet();
            }
        };
    }

    @Override
    public BdLogger logger() {
        return new BdLogger() {
            @Override
            public void debug(String message) {
                log.add("DEBUG " + message);
            }

            @Override
            public void info(String message) {
                log.add("INFO " + message);
            }

            @Override
            public void warn(String message) {
                log.add("WARN " + message);
            }

            @Override
            public void error(String message, Throwable error) {
                log.add("ERROR " + message);
            }
        };
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public ProtocolBridge protocol() {
        return protocol;
    }

    @Override
    public String pluginVersion() {
        return "0.0.0-test";
    }

    private record AvailableBridge(MinecraftVersion version) implements ProtocolBridge {
        @Override
        public String name() {
            return "fake";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String libraryVersion() {
            return "1";
        }

        @Override
        public Optional<MinecraftVersion> serverVersion() {
            return Optional.ofNullable(version);
        }

        @Override
        public boolean attach(io.github.intruder_tech.blackdoor.protocol.packet.PacketHandler handler) {
            return true;
        }

        @Override
        public Optional<MinecraftVersion> clientVersion(Object platformPlayer) {
            return Optional.empty();
        }
    }
}
