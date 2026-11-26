# Adaptive Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** During a flood of logins from many addresses, addresses that played before keep getting in and unknown ones share a small allowance.

**Architecture:** `blackdoor-network` gains a bounded known-address list and a flood detector, both driven by `ConnectionGuard.loginStart`. `blackdoor-core` passes join and leave signals from the Bukkit, Velocity and BungeeCord adapters to the guard, and exposes the setting, GUI item and diagnostics.

**Tech Stack:** Java 17 bytecode (Velocity module 25), Gradle multi-module, JUnit 5, AssertJ.

**Spec:** [docs/superpowers/specs/2026-11-26-adaptive-mode-design.md](../specs/2026-11-26-adaptive-mode-design.md)

## Global Constraints

- Defaults: `adaptive.mode: monitor`, `trigger-per-second: 10`, `unknown-per-second: 2`, `calm-seconds: 60`.
- Ranges: both rates 1 to 10000, `calm-seconds` 10 to 3600.
- Known address: a player from it stayed at least 60 seconds. Averaging window: 10 seconds.
- Refusing needs `adaptive.mode: mitigate` **and** `config.yml` `mode: enforce`. Network `mode: disabled` turns adaptive mode off. Network `mode: monitor` does not stop adaptive mode from blocking.
- Exempt addresses and backends behind forwarding are neither counted nor refused.
- No IP bans, no player addresses written to disk.
- Detection fails open: any internal error lets the connection through.
- The compiler runs with `-Werror -Xlint:all`. Only `blackdoor-protocol-packetevents` imports PacketEvents.
- Build: `export JAVA_HOME="$HOME/.jdks/jdk-21.0.13.1+2"` then `./gradlew <task>` from the repo root.
- Commit and push only when the owner asks. Where a task says "Commit", stage and commit only if the owner has asked for commits; end the message with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Work on branch `feat/adaptive-mode`.

## Review Focus

1. Two players from one address, one leaves early: the address stays known while the other has been on 60 seconds (Task 1).
2. A login refused by the per-address limit counts toward the trigger but uses none of the unknown allowance (Task 2).
3. A leave with no recorded join (failed login, plugin loaded late) changes nothing (Task 1).
4. A user's older `network.yml` has no `adaptive` block: defaults apply and the GUI switch still saves (Task 3).
5. A second login with the same account refused by Velocity must not forget the online player (Task 4).

---

### Task 1: Settings and known addresses

**Files:**
- Modify: `blackdoor-network/src/main/java/io/github/intruder_tech/blackdoor/network/NetworkSettings.java`
- Create: `blackdoor-network/src/main/java/io/github/intruder_tech/blackdoor/network/KnownAddresses.java`
- Modify: `blackdoor-core/src/main/resources/blackdoor-defaults/network.yml`
- Test: `blackdoor-network/src/test/java/io/github/intruder_tech/blackdoor/network/KnownAddressesTest.java`

**Interfaces:**
- Produces: `NetworkSettings.Adaptive(Mode mode, int triggerPerSecond, int unknownPerSecond, int calmSeconds)`; `NetworkSettings.adaptive()` as the 10th record component. `Adaptive.defaults()` returns `(MONITOR, 10, 2, 60)`. The existing 8- and 9-argument constructors stay and use `new Adaptive(Mode.DISABLED, 10, 2, 60)`; `NetworkSettings.defaults()` uses `Adaptive.defaults()`.
- Produces: package-private `KnownAddresses(TimeSource time)` with `void joined(UUID id, InetAddress address)`, `void left(UUID id, int maxKnown)`, `boolean isKnown(InetAddress address)`, `int size()` (remembered addresses). All synchronized.

- [ ] **Step 1: Write the failing tests**

```java
class KnownAddressesTest {
    private final ManualTimeSource time = new ManualTimeSource();
    private final KnownAddresses known = new KnownAddresses(time);
    private final InetAddress a = ip("203.0.113.5");
    private final UUID one = UUID.randomUUID();
    private final UUID two = UUID.randomUUID();

    @Test
    void anAddressBecomesKnownAfterSixtySeconds() {
        known.joined(one, a);
        time.advance(Duration.ofSeconds(59));
        assertThat(known.isKnown(a)).isFalse();
        time.advance(Duration.ofSeconds(1));
        assertThat(known.isKnown(a)).as("still online").isTrue();
        known.left(one, 10);
        assertThat(known.isKnown(a)).as("remembered").isTrue();
        assertThat(known.size()).isEqualTo(1);
    }

    @Test
    void aShortStayIsNotRemembered() {
        known.joined(one, a);
        time.advance(Duration.ofSeconds(30));
        known.left(one, 10);
        assertThat(known.isKnown(a)).isFalse();
        assertThat(known.size()).isZero();
    }

    @Test
    void anotherPlayerLeavingEarlyDoesNotForgetTheAddress() {
        known.joined(one, a);
        time.advance(Duration.ofSeconds(60));
        known.joined(two, a);
        known.left(two, 10);
        assertThat(known.isKnown(a)).isTrue();
    }

    @Test
    void aLeaveWithoutAJoinChangesNothing() {
        known.left(one, 10);
        assertThat(known.size()).isZero();
    }

    @Test
    void theOldestAddressIsForgottenWhenFull() {
        for (int i = 1; i <= 3; i++) {
            UUID id = UUID.randomUUID();
            known.joined(id, ip("203.0.113." + i));
            time.advance(Duration.ofSeconds(60));
            known.left(id, 2);
        }
        assertThat(known.size()).isEqualTo(2);
        assertThat(known.isKnown(ip("203.0.113.1"))).isFalse();
    }
}
```

`ip(String)` is the same helper as in `ConnectionGuardTest`.

- [ ] **Step 2:** Run `./gradlew :blackdoor-network:test`. Expected: compile failure, `KnownAddresses` not found.
- [ ] **Step 3:** Implement. Online players are a `Map<UUID, (InetAddress, long sinceNanos)>`; `isKnown` checks the remembered set, then any online entry for that address that is 60 seconds old. Remembered addresses are an access-ordered `LinkedHashMap`, trimmed to `maxKnown`. Ignore a null id or address. In `NetworkSettings.parse`, read `adaptive.mode`, `adaptive.trigger-per-second`, `adaptive.unknown-per-second`, `adaptive.calm-seconds` with the ranges above.
- [ ] **Step 4:** Add to `network.yml` after `allowed-proxies`:

```yaml
# Adaptive mode: during a flood of logins from many addresses, addresses that played here
# before (for at least 60 seconds) keep getting in, and all others share a small allowance.
# The list of known addresses is kept in memory only and is empty after a restart.
# disabled = off, monitor = report only, mitigate = refuse (needs config.yml mode "enforce")
adaptive:
  mode: monitor
  # Logins per second from unknown addresses, averaged over 10 seconds, that start attack mode.
  trigger-per-second: 10
  # Logins per second still allowed from unknown addresses while attack mode is on.
  unknown-per-second: 2
  # Attack mode ends after the rate has stayed at or below the trigger for this long.
  calm-seconds: 60
```

- [ ] **Step 5:** Run `./gradlew :blackdoor-network:test`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat: adaptive mode settings and known addresses`

### Task 2: Flood detector and guard

**Files:**
- Create: `blackdoor-network/src/main/java/io/github/intruder_tech/blackdoor/network/FloodDetector.java`
- Modify: `blackdoor-network/src/main/java/io/github/intruder_tech/blackdoor/network/ConnectionGuard.java`
- Test: `blackdoor-network/src/test/java/io/github/intruder_tech/blackdoor/network/AdaptiveModeTest.java`

**Interfaces:**
- Consumes: `NetworkSettings.adaptive()`, `KnownAddresses` (Task 1).
- Produces on `ConnectionGuard`:
  - a 6-argument constructor adding `Consumer<Boolean> attackChanged` (true = started, false = ended); the 5-argument one passes a no-op.
  - `void joined(UUID id, InetAddress address)`, `void left(UUID id)`
  - `record AttackStatus(boolean active, int knownAddresses, long refused)` and `AttackStatus attackStatus()`
- Check ids: `network.attack-mode` (once per start), `network.unknown-login` (at most once per second). Subject `new Subject(null, address.getHostAddress())`, confidence `HEURISTIC`.

- [ ] **Step 1: Write the failing tests**

```java
class AdaptiveModeTest {
    private final ManualTimeSource time = new ManualTimeSource();
    private final List<Finding> findings = new ArrayList<>();
    private final List<Boolean> changes = new ArrayList<>();
    private final AtomicReference<NetworkSettings> settings = new AtomicReference<>(cfg(NetworkSettings.Mode.MITIGATE));
    private final AtomicBoolean enforce = new AtomicBoolean(true);
    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final ConnectionGuard guard = new ConnectionGuard(findings::add, time, settings::get, enforce::get,
            forwarded::get, changes::add);
    private int next;

    /** Per-address limits out of the way; trigger 2/s (more than 20 in 10 s), allowance 1/s, calm 10 s. */
    private static NetworkSettings cfg(NetworkSettings.Mode adaptive) {
        NetworkSettings.Limit wide = new NetworkSettings.Limit(1000, 1000);
        return new NetworkSettings(NetworkSettings.Mode.MONITOR, wide, wide, wide, 255, 1000, Set.of("203.0.113.99"),
                true, Set.of(), new NetworkSettings.Adaptive(adaptive, 2, 1, 10));
    }

    /** A login from a new public address each time. */
    private boolean stranger() {
        next++;
        return guard.loginStart(ip("198.51." + next / 250 + "." + (next % 250 + 1)), "Bot");
    }

    private long refusedOf(int attempts) {
        return IntStream.range(0, attempts).filter(i -> stranger()).count();
    }

    @Test
    void startsAboveTheTriggerAndSharesTheAllowance() {
        assertThat(refusedOf(20)).as("at the trigger").isZero();
        assertThat(guard.attackStatus().active()).isFalse();
        assertThat(stranger()).as("21st takes the single allowance").isFalse();
        assertThat(guard.attackStatus().active()).isTrue();
        assertThat(stranger()).isTrue();
        assertThat(changes).containsExactly(true);
        assertThat(findings).extracting(Finding::checkId)
                .containsExactly("network.attack-mode", "network.unknown-login");
        assertThat(guard.attackStatus().refused()).isEqualTo(1);
    }

    @Test
    void knownAddressesStillGetIn() {
        InetAddress home = ip("203.0.113.5");
        UUID id = UUID.randomUUID();
        guard.joined(id, home);
        time.advance(Duration.ofSeconds(60));
        guard.left(id);
        refusedOf(50);
        assertThat(guard.loginStart(home, "Steve")).isFalse();
        assertThat(guard.attackStatus().knownAddresses()).isEqualTo(1);
    }

    @Test
    void endsAfterTheCalmPeriod() {
        refusedOf(30);
        time.advance(Duration.ofSeconds(9));
        assertThat(guard.attackStatus().active()).isTrue();
        time.advance(Duration.ofSeconds(2));
        assertThat(guard.attackStatus().active()).isFalse();
        assertThat(changes).containsExactly(true, false);
        assertThat(stranger()).isFalse();
    }

    @Test
    void watchModeAndWatchProtectionRefuseNothing() {
        settings.set(cfg(NetworkSettings.Mode.MONITOR));
        assertThat(refusedOf(50)).isZero();
        assertThat(guard.attackStatus().active()).isTrue();
        settings.set(cfg(NetworkSettings.Mode.MITIGATE));
        enforce.set(false);
        assertThat(refusedOf(50)).isZero();
    }

    @Test
    void switchedOffExemptAndForwardedAreNotCounted() {
        settings.set(cfg(NetworkSettings.Mode.DISABLED));
        refusedOf(50);
        settings.set(cfg(NetworkSettings.Mode.MITIGATE));
        for (int i = 0; i < 50; i++) {
            guard.loginStart(ip("203.0.113.99"), "Bot");
            guard.loginStart(ip("10.0.0.4"), "Bot");
        }
        forwarded.set(true);
        refusedOf(50);
        assertThat(guard.attackStatus().active()).isFalse();
        assertThat(findings).isEmpty();
    }

    @Test
    void aLoginRefusedByTheAddressLimitCountsButUsesNoAllowance() {
        NetworkSettings.Limit one = new NetworkSettings.Limit(1, 1);
        NetworkSettings.Limit wide = new NetworkSettings.Limit(1000, 1000);
        settings.set(new NetworkSettings(NetworkSettings.Mode.MITIGATE, wide, wide, one, 255, 1000, Set.of(),
                true, Set.of(), new NetworkSettings.Adaptive(NetworkSettings.Mode.MITIGATE, 2, 1, 10)));
        InetAddress noisy = ip("203.0.113.7");
        for (int i = 0; i < 30; i++) {
            guard.loginStart(noisy, "Bot");
        }
        assertThat(guard.attackStatus().active()).isTrue();
        assertThat(stranger()).as("allowance untouched").isFalse();
    }
}
```

- [ ] **Step 2:** Run `./gradlew :blackdoor-network:test`. Expected: compile failure.
- [ ] **Step 3:** Implement `FloodDetector` (package-private, synchronized): ten one-second counters from `TimeSource.nanoTime()`. It becomes active when a recorded attempt makes the 10-second sum exceed `triggerPerSecond * 10`. The "last above the trigger" time moves **only when an attempt is recorded**, never on a status read. It goes inactive once `calmSeconds` have passed since that time. Expiry is checked on each record and status read; there is no thread. A fresh `TokenBucket(unknownPerSecond, unknownPerSecond, time)` is created at each start. In `ConnectionGuard.loginStart`, after the rate and name checks: skip if adaptive mode is `DISABLED`, forwarded, exempt or known; otherwise record the attempt; if active, the login is not already refused, and the bucket is empty, report, and refuse when adaptive is `MITIGATE` and enforcement is allowed. A refused login counts once in the existing `refused()`; `AttackStatus.refused` counts adaptive refusals only. `joined`/`left` delegate to `KnownAddresses` with `maxTrackedAddresses`. Call `attackChanged` outside any lock.
- [ ] **Step 4:** Run `./gradlew :blackdoor-network:test`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: adaptive mode for login floods`

### Task 3: Engine, setting, GUI and diagnostics

**Files:**
- Modify: `blackdoor-core/src/main/java/io/github/intruder_tech/blackdoor/core/` `BlackDoorEngine.java`, `settings/SettingsService.java`, `menu/BlackDoorMenus.java`, `menu/Icon.java`, `command/sub/DiagnosticsCommand.java`
- Modify: `blackdoor-platform-bukkit/src/main/java/io/github/intruder_tech/blackdoor/platform/bukkit/MenuItems.java`
- Test: `blackdoor-core/src/test/java/io/github/intruder_tech/blackdoor/core/AdaptiveWiringTest.java`, `core/menu/MenuTest.java`

**Interfaces:**
- Consumes: Task 2's `ConnectionGuard` API.
- Produces: `public void BlackDoorEngine.playerJoined(UUID id, InetAddress address)` and `public void playerLeft(UUID id)` (safe when there is no guard; errors are logged, never thrown); `SettingsService.ADAPTIVE_MODE = "network.adaptive"` (file `network.yml`, path `adaptive.mode`, options of `NetworkSettings.Mode`); `Icon.BOT_FLOOD` mapped to `"ZOMBIE_HEAD", "ROTTEN_FLESH"`.
- Engine passes `attackChanged` to the guard: `logger.warn("Attack mode started: unknown addresses are limited.")` (when not blocking: `"Attack mode started (watch only): nothing is refused."`) and `logger.info("Attack mode ended.")`.

Main menu becomes 5 rows: slot 4 setup; 10, 12, 14, 16 unchanged; 20 join flood, 22 bot flood, 24 login; 30 status, 32 reload; 40 close.

GUI item: name `Bot flood mode | <Off/Watch/Block>`; lore `Keeps regular players able to join`, `while bots flood the server.`, mode line, `Attack right now: Yes/No`, `Known addresses: <n>`, the "Will only block once Protection is ON" line as on checks, `Click to change`. Glows on Block; `CHECK_DISABLED` icon when Off. Status lore adds `Bot flood mode: ACTIVE` only while active. Diagnostics adds row `Bot flood mode`: `idle` or `ACTIVE`, known addresses, refused count.

- [ ] **Step 1: Write the failing tests.** In `MenuTest` change the slots: status 23 to 30, close 31 to 40, login 21 to 24. Add:

```java
    @Test
    void botFloodModeCanBeSwitchedInGameOnAnOlderNetworkFile() throws Exception {
        Path file = dir.resolve("network.yml");
        Files.writeString(file, Files.readString(file).replaceAll("(?s)\\nadaptive:.*?calm-seconds: \\d+", ""));
        assertThat(engine.reloadConfiguration("test").success()).isTrue();
        RecordingSender admin = RecordingSender.admin();
        FakeSession s = new FakeSession(admin, engine.menus().main());
        assertThat(s.shown.itemAt(22).orElseThrow().name()).contains("Bot flood mode").contains("Watch");
        s.click(22);
        AwaitMessages.await(admin, m -> m.contains("Bot flood mode is now Block"));
        assertThat(engine.networkSettings().adaptive().mode().name()).isEqualTo("MITIGATE");
    }
```

`AdaptiveWiringTest` builds the engine as `ProxyGuardTest` does (`PlatformType.VELOCITY`), with a `ManualTimeSource time`:

```java
    @Test
    void aPlayerWhoStayedIsKnownAndTheStatusIsInDiagnostics() throws Exception {
        UUID id = UUID.randomUUID();
        engine.playerJoined(id, InetAddress.getByName("203.0.113.5"));
        time.advance(Duration.ofSeconds(60));
        engine.playerLeft(id);
        assertThat(engine.connectionGuard().orElseThrow().attackStatus().knownAddresses()).isEqualTo(1);
        RecordingSender sender = RecordingSender.admin();
        engine.commands().dispatch(sender, new String[]{"diagnostics"});
        assertThat(sender.all()).contains("Bot flood mode").contains("idle");
    }
```

`RecordingSender` is in the `core.command` test package; make it visible if needed. Add `testImplementation(testFixtures(project(":blackdoor-common")))` to `blackdoor-core` if missing.

- [ ] **Step 2:** Run `./gradlew :blackdoor-core:test`. Expected: compile failure.
- [ ] **Step 3:** Implement as above. `SettingsService.current` reads `engine.networkSettings().adaptive().mode()`.
- [ ] **Step 4:** Run `./gradlew :blackdoor-core:test`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `feat: bot flood mode setting, GUI item and diagnostics`

### Task 4: Platform join and leave signals

**Files:**
- Create: `blackdoor-platform-bukkit/src/main/java/io/github/intruder_tech/blackdoor/platform/bukkit/PresenceListener.java`
- Modify: `BlackDoorBukkitPlugin.java` (same package)
- Modify: `blackdoor-proxy-velocity/.../BlackDoorVelocityPlugin.java`
- Modify: `blackdoor-proxy-bungee/.../BungeeGuardListener.java`, `BlackDoorBungeePlugin.java`

**Interfaces:**
- Consumes: `engine.playerJoined(UUID, InetAddress)`, `engine.playerLeft(UUID)`.

These handlers are one call each against server APIs that the unit tests cannot run. Task 5's live run verifies Bukkit; the proxy handlers are only compiled.

- [ ] **Step 1:** Bukkit `PresenceListener(BlackDoorEngine engine) implements Listener`: `PlayerJoinEvent` and `PlayerQuitEvent`, both `MONITOR`, address from `player.getAddress()` (skip when null). Register it in `onEnable` whether or not the login system is on.
- [ ] **Step 2:** Velocity: `@Subscribe` `PostLoginEvent` calls `playerJoined`; `DisconnectEvent` calls `playerLeft` unless `getLoginStatus()` is `CONFLICTING_LOGIN`.
- [ ] **Step 3:** BungeeCord: `PostLoginEvent` and `PlayerDisconnectEvent`. `BungeeGuardListener` also takes the engine. Non-network sockets are skipped with the existing `address(...)` helper.
- [ ] **Step 4:** Run `./gradlew build`. Expected: BUILD SUCCESSFUL, no warnings.
- [ ] **Step 5: Commit** `feat: report player joins and leaves to the connection guard`

### Task 5: Live check and documents

**Files:**
- Modify: `docs/CONFIGURATION.md` (settings, and the "not implemented" line about adaptive mode), `docs/NETWORK_SECURITY.md` §2.3, `docs/DDOS_LIMITATIONS.md` if its wording now overstates, `docs/KNOWN_LIMITATIONS.md` (the spec's Known limits), `IMPLEMENTATION.md` §14 and §11 (the Next Steps list is out of date), `README.md` if it lists features.

- [ ] **Step 1:** `./gradlew build`. Expected: BUILD SUCCESSFUL, 0 test failures.
- [ ] **Step 2:** Live run on a copy of the scratch 1.21.11 server (offline mode, bound to 127.0.0.1), never the owner's server. Set `exempt-private-addresses: false`, `adaptive.mode: mitigate`, `trigger-per-second: 2`, `config.yml` `mode: enforce`, and raise the `connections` and `logins` limits so only adaptive mode can refuse.
- [ ] **Step 3:** Expected, in order: a bot from 127.0.0.1 stays 65 s and leaves; 40 quick logins from 127.0.0.2 to 127.0.0.41 (`localAddress` in the connect options) start attack mode (log `Attack mode started`) and most are refused; the 127.0.0.1 bot gets in during the attack; after `calm-seconds` the next login logs `Attack mode ended.`
- [ ] **Step 4:** Update the documents. State plainly what was not verified: a real proxy, a distributed flood from real addresses.
- [ ] **Step 5: Commit** `docs: adaptive mode`
