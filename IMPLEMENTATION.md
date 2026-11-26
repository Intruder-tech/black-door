# Black Door — Implementation Tracker

> The single place to see where the project stands. Updated at the end of every work session
> and at every phase gate. Reports what was done, what was verified, what changed, what is
> blocked, and what needs the owner's decision.

**Last updated:** 2026-11-26
**Current phase:** Phase 4 — Exploit and network protection (owner override: started before Phase 2
and 3 are complete)
**Phase status:** Milestones 2.1, 2.2, 2.3 and 2.4a done. **2.4b (physics) paused by the owner** after
step 1 (recorder). Phase 4: milestones 4.1 (exploit guards, first set) and 4.2 (backend connection
protection) built, not yet committed (§14).
**Protections active:** 12 packet checks and join/ping flood protection, **monitor mode by default**
(they log findings; they block only if set to `mitigate` and the global mode is `enforce`).
**Next gate:** Owner review of 4.1/4.2 and a real player session (false-positive check for books,
signs, creative items and mod messages).

---

## 1. Status Dashboard

| Phase | Name | Status | Started | Gate passed |
|---|---|---|---|---|
| 0 | Research, feasibility, threat model, architecture | **Done** | 2026-11-26 | 2026-11-26 (owner: "Proceeded with Phase I") |
| 1 | Core architecture, build, adapters, protocol abstraction, config | **Done** | 2026-11-26 | 2026-11-26 (owner: "approved, proceed with Phase 2") |
| 2 | Packet inspection, player state, physics | **In progress** (2.1 done) | 2026-11-26 | — |
| 3 | Combat / movement / inventory / interaction detections | Not started | — | — |
| 4 | Exploits, app-layer network protection, proxies | **In progress** (4.1, 4.2; §14) | 2026-11-26 | — |
| 5 | Behavior analysis, correlation, enforcement | Not started | — | — |
| 6 | Storage, administration, telemetry, developer API | Not started | — | — |
| 7 | Cross-version testing, security audit, optimization | Not started | — | — |
| 8 | Release engineering, docs, deployment | Not started | — | — |

Status values: Not started · In progress · In review · Blocked · Done.

## 2. Blocked / Needs Owner Action

| ID | Item | Why blocked | What is needed from you |
|---|---|---|---|
| B-1 | Smoke test on real Velocity and BungeeCord | **Resolved 2026-11-26.** Owner allowed local test proxies. | — |
| B-2 | Smoke test on real Paper / Spigot / Folia | **Resolved 2026-11-26.** Owner allowed local test servers and accepted the Minecraft EULA for them. | — |
| B-3 | CI on GitHub | **Resolved 2026-11-26.** Pushed to `github.com/Intruder-tech/black-door` (private). Run #2 is green. | — |
| B-4 | Real player traffic | **Largely resolved 2026-11-26.** The owner played ~4 minutes on a local Paper 26.3 server with a 26.3 client (creative and survival, mob combat). Result: **7,715 packets inspected, 0 findings, 0 blocked, 0 errors/exceptions in the log**. `/blackdoor setup` and `/blackdoor player` ran in game; the GUI opened, and the global mode was switched to enforce and back via clicks (both audited). config.yml comments survived the edits. Not covered: per-check mode clicks, older/translated clients, Folia with a player, load. | Confirm in chat whether the GUI looked right and that menu items could not be taken. |
| B-5 | Physics golden traces (milestone 2.4) | Prediction checks must produce zero flags on recordings of legitimate play, which can only come from real clients. | Later: record test sessions on a local server (procedure to be written in milestone 2.4). |

## 3. Decisions (resolved this session)

| ID | Decision | Outcome |
|---|---|---|
| OD-1 | Minimum runtime | Java 17 (Velocity: Java 25, forced by Velocity API 4.x) |
| OD-2 | License | GPL-3.0 (LICENSE added) |
| OD-4 | Package root | Maven group `io.github.intruder-tech`, Java package `io.github.intruder_tech.blackdoor` |
| OD-5 | Tested version set | Representative subset of 13 versions |
| OD-6 | PacketEvents | Separate plugin, **soft** dependency (ADR-014) |

Still open: OD-3 (world-view memory, Phase 2), OD-7, OD-8, OD-9. See
[docs/ARCHITECTURAL_REVIEW.md §7](docs/ARCHITECTURAL_REVIEW.md).

## 4. Phase 1 — What Was Built

### 4.1 Modules

| Module | Contents | Tests |
|---|---|---|
| `blackdoor-api` | `BlackDoor` (version info), `BlackDoorProvider` | via core |
| `blackdoor-common` | `TokenBucket`, `TimeSource` (+ test clock), `BdLogger`, `RateLimitedLogger` (bounded categories), `LogSanitizer`, `BoundedExecutor` (bounded queue, never blocks, named daemon threads) | 17 |
| `blackdoor-config` | Safe YAML loader (SnakeYAML `SafeConstructor`; alias/depth/size/duplicate limits), `ConfigReader` (typed, collects all errors, unknown-key warnings), versioned migrations with backups, atomic file writes, all-or-nothing `ConfigManager` with fallback to bundled defaults | 24 |
| `blackdoor-protocol` | `MinecraftVersion` (1.x and year-based 26.x), `ProtocolFeature` table (client/server side, `verified` flag), `PlayerVersions`, `VersionSupport` (latest known: 26.3), `ProtocolBridge` SPI | 26 |
| `blackdoor-protocol-packetevents` | `PacketEventsBridge` (fully defensive), version mapping; only module importing PacketEvents | 4 |
| `blackdoor-core` | `BlackDoorEngine` lifecycle (enable/disable/degraded), platform SPI (`Platform`, `PlatformScheduler`, `PlatformInfo`), `CommandRouter` (permission per subcommand, input bounds, tab-complete filtering, error containment), commands `help` `status` `version` `reload` `diagnostics`, `AuditLog` (JSON lines), `config.yml` | 25 |
| `blackdoor-platform-bukkit` | Entry point for all Bukkit-family servers; platform detection (Folia/Purpur/Paper/Spigot/Bukkit); main-thread scheduler; thread-safe sender | none (needs a server — B-2) |
| `blackdoor-platform-paper` | Region/entity scheduler for Paper 1.20+ and Folia, loaded at runtime | none (B-2) |
| `blackdoor-proxy-velocity` | Velocity 4.x plugin, `/blackdoorproxy` | none (B-1) |
| `blackdoor-proxy-bungee` | BungeeCord plugin, `/blackdoorproxy` | none (B-1) |
| `dist-bukkit` | Universal shaded jar + `plugin.yml` (`folia-supported: true`, `softdepend: packetevents`, per-command permissions) | jar inspected (§5) |

### 4.2 Build & supply chain

- Gradle 9.4.1 wrapper, Kotlin DSL, convention plugin in `build-logic/`, version catalog
  `gradle/libs.versions.toml`.
- Toolchain JDK 25 (auto-provisioned); `--release 17` everywhere except Velocity (`25`).
  `-Werror` with all lint warnings on.
- Repositories restricted to the groups expected from each (dependency-confusion mitigation).
- Dependency locking: `gradle.lockfile` in every module.
- `verifyModuleGraph`: fails the build if any module depends sideways or upward
  (layering from docs/ARCHITECTURE.md §3), or if anything but the Bukkit adapter uses the
  PacketEvents adapter.
- `verifySourceRules`: fails the build on process execution (SR-80), Java deserialization
  (SR-31), server internals imports (ADR-010), PacketEvents imports outside its adapter
  (ADR-003), or `System.exit`.
- CI workflow `.github/workflows/ci.yml` (build, tests, jar artifacts, OSV dependency scan).
  **Never executed** (B-3).

### 4.3 Deviations from the Phase 0 design (recorded as ADRs)

| Change | Why | ADR |
|---|---|---|
| SnakeYAML directly instead of Configurate | One fewer dependency; explicit validation; safe loader options | ADR-012 |
| JDK 25 build toolchain; Velocity module targets Java 25 | Velocity API 4.x is Java 25 bytecode | ADR-013 |
| PacketEvents is `softdepend`, missing ⇒ DEGRADED | Operators see the problem instead of a silently missing plugin | ADR-014 |
| No `dist-velocity` / `dist-bungee` modules | One adapter per proxy, so the adapter shades its own jar | ADR-015 |
| Proxy command is `/blackdoorproxy` (alias `/bdproxy`) | Proxies intercept commands before the backend | ADR-016 |
| No `blackdoor-testing` module yet | Phase 1 fixtures fit in Gradle test fixtures; the module arrives with the replay harness in Phase 2 | — |

## 5. Verification Evidence (Phase 1)

| Check | Command | Result (2026-11-26) |
|---|---|---|
| Full build + all unit tests | `./gradlew clean build` | **BUILD SUCCESSFUL** |
| Unit tests | (part of build) | **96 passed, 0 failed, 0 skipped** (common 17, config 24, protocol 26, packetevents 4, core 25) |
| Module layering | `./gradlew verifyModuleGraph` | OK (11 modules). Negative test: making `api` depend on `common` was rejected. |
| Source rules | `./gradlew verifySourceRules` | OK. Negative test: a file containing `new ProcessBuilder` was rejected. |
| Universal jar contents | inspection with `jar`/`javap` | `plugin.yml` version expanded; SnakeYAML relocated (0 unrelocated classes); 0 PacketEvents classes bundled; Paper scheduler present; bytecode Java 17 (major 61). |
| Velocity descriptor | `velocity-plugin.json` in jar | Generated correctly (id `blackdoor`, version `0.1.0-SNAPSHOT`). |
| Lockfiles | `./gradlew build --write-locks`, then `build` | 11 lockfiles; build passes against them. |
| PacketEvents version table | `PacketEventsVersionsTest` | Every PacketEvents 2.14.0 client/server version maps; newest = 26.3 = `LATEST_KNOWN`. |
| Security-relevant config tests | `YamlLoaderTest`, `ConfigManagerTest` | Alias bomb, `!!javax.script.ScriptEngineManager` gadget, duplicate keys, 100-level nesting, `../` file names: all rejected. |
| Lifecycle / leaks | `EngineLifecycleTest` | 5 enable/disable cycles leave **0** Black Door threads; API unregistered; scheduler tasks cancelled. |
| Real server / proxy start | §6.3 runner, localhost only | **Pass on 9 of 9 configurations** (§5.1). |
| CI on GitHub | push to `main` | Run 45692832610 **failed** and exposed two real issues: a thread-exit race in `BoundedExecutor.shutdown` (the leak test saw 1 thread) and a message-ordering race in `ReloadCommandTest`. OSV also flagged old libraries that come only from compile-only platform APIs (Spigot 1.8.8 guava 18 / snakeyaml 1.15 / gson 2.3.1; Paper and proxy APIs). Fixed in `3697b84`; run **45694262245 green** (build + tests + OSV on shipped deps). Shipped jars contain one external library: SnakeYAML 2.7 (verified via `runtimeClasspath`). |
| Test stability | `./gradlew test --rerun` x8 | 0 failures in 8 runs after the fixes. |
| Tests on a Java 17 runtime | — | **Not run.** Tests ran on the JDK 25 toolchain; servers ran on Java 21/25. No Java 17 JVM is installed. Planned CI addition. |

### 5.1 Real-platform smoke tests (2026-11-26)

Scope per run: start the server with Black Door (and PacketEvents where noted). Run `status`,
`version`, `reload` and `diagnostics` from the console, stop the server, then check the log for
exceptions. Every server/proxy jar was downloaded from its official source with its SHA-256
checked. All runs were bound to 127.0.0.1. **No player connected**, so per-player scheduling
(`runForPlayer`) was not exercised.

| Platform | Version / build | Java | PacketEvents | Detected as | State | Commands | Clean stop | Notes |
|---|---|---|---|---|---|---|---|---|
| Velocity | 4.3.1 #45 | 25 | — | VELOCITY | RUNNING | all OK | yes | `/blackdoorproxy` |
| BungeeCord | latest CI build | 21 | — | BUNGEECORD | RUNNING | all OK | yes | |
| Paper | 26.3 #262 | 25 | 2.14.0 | PAPER | RUNNING | all OK | yes | "Minecraft 26.3: known" |
| Folia | 26.3 #28 | 25 | 2.14.0 | FOLIA | RUNNING | all OK | yes | Reload reply sent through the region scheduler |
| Paper | 1.21.11 #132 | 21 | 2.14.0 | PAPER | RUNNING | all OK | yes | |
| Paper | 1.21.11 #132 | 21 | **absent** | PAPER | **DEGRADED** (as designed) | all OK | yes | Reason shown in log and `status` |
| Paper | 1.20.1 #196 | 21 | 2.14.0 | PAPER | RUNNING | all OK | yes | Oldest API with region schedulers |
| Spigot | 26.3 (built locally with BuildTools #202) | 25 | 2.14.0 | SPIGOT | RUNNING | all OK | yes | No region schedulers: the async reload reply came back on the main "Server thread" via the Bukkit scheduler, as designed. The first BuildTools run failed on a transient network timeout; the retry succeeded. |
| Paper (PaperSpigot) | 1.8.8 #445 | 21 | 2.14.0 | PAPER | RUNNING | all OK | yes | First run found a **bug**: detected as SPIGOT. Fixed in `BukkitEnvironment` (legacy `org.github.paperspigot` package), re-tested. The server's own `NoSuchFieldException: modifiers` warning comes from PaperSpigot on modern Java, not from Black Door. |

### Phase 1 acceptance criteria (from docs/ROADMAP.md)

| Criterion | Status |
|---|---|
| Plugin loads and unloads without errors or leaks on every CI-matrix platform | **Mostly met.** Manual smoke tests pass on 9 configurations (§5.1). Not yet covered: player connections, Purpur, Java 17 runtime, and running these tests automatically in CI. |
| Config reload rollback tested | **Met** (unit tests). |
| Dependency graph check passes | **Met.** |
| No feature is advertised | **Met.** Descriptors, `/blackdoor status` and docs all state that no protections are active. |
| CI pipeline: build, unit, dependency rules, static security rules, SBOM, vuln scan | **Partial.** Build, unit, dependency and static rules work locally. The OSV scan is defined but has never run. **SBOM not added yet.** |

## 6. Procedures

### 6.1 Phase gate
1. All phase deliverables exist and match the ROADMAP acceptance criteria.
2. Tests named in the acceptance criteria pass; results recorded in §5 with date and command.
3. Docs updated: feature status labels, compatibility matrix (from real test results only),
   known limitations.
4. Security review of the phase against docs/SECURITY_REQUIREMENTS.md; findings in
   docs/SECURITY_AUDIT.md.
5. Owner approval recorded in §8 before the next phase starts.

### 6.2 Work session
1. Read this file first; continue from §10 "Next steps".
2. Do the work; run the relevant tests.
3. Append to §7 (log): what changed, what was verified, what failed, what is blocked.
4. Update §1, §2, §5 and §10.

### 6.3 Local smoke test (for B-1 / B-2, run in a throwaway folder, localhost only)
1. Build: `./gradlew build`.
2. Velocity: put `BlackDoor-Velocity-*.jar` in `plugins/`, set `bind = "127.0.0.1:25577"` in
   `velocity.toml`, and start with Java 25. Run `blackdoorproxy status`, `blackdoorproxy reload`
   and `blackdoorproxy diagnostics`, then `shutdown`. Expect no stack traces, state `RUNNING`,
   and `plugins/blackdoor/config.yml` created.
3. Paper/Folia/Spigot: accept the EULA yourself. Install PacketEvents and `BlackDoor-Bukkit-*.jar`,
   bind the server to `127.0.0.1`, and run `blackdoor status`, `reload` and `diagnostics`, then
   `stop`. Without PacketEvents, expect `DEGRADED` with the reason shown.

### 6.4 Reporting rules
- Report test failures as failures, with the decisive output line.
- Never mark something Done without verification evidence.
- Never record benchmark numbers, accuracy figures or compatibility not actually measured.
- State skipped steps as skipped, with the reason.

### 6.5 New Minecraft version onboarding
See docs/PROTOCOL_ARCHITECTURE.md §6. Raising `VersionSupport.LATEST_KNOWN` is part of it, and
`PacketEventsVersionsTest` fails if it disagrees with the pinned PacketEvents release. Log each
onboarding in §9.

### 6.6 Environment notes
- No system JDK. A portable Temurin JDK 21.0.13.1 lives in `~/.jdks/` (checksum verified).
  Set `JAVA_HOME` to it to run Gradle. Gradle provisions JDK 25 into `~/.gradle/jdks/`.
- When writing files through the shell tool, `\\` was collapsed to `\` and long commands were
  truncated. Source code avoids backslash escapes where possible (for example regex `[.]`
  instead of an escaped dot).

## 7. Activity Log

| Date | Phase | Summary | Verification | Notes |
|---|---|---|---|---|
| 2026-11-26 | 0 | Researched ecosystem state. Wrote all Phase 0 documents and this tracker. | Documents written; `[verify]` facts flagged. | — |
| 2026-11-26 | 0→1 | Owner approved Phase 0 and resolved OD-1/2/4/5/6. | Recorded in §3. | — |
| 2026-11-26 | 1 | Installed a portable JDK 21 (checksum verified), initialized git (no commits). Built the Gradle multi-module foundation: 10 code modules + universal Bukkit jar, two proxy jars, config framework, version model, PacketEvents adapter, platform adapters, admin commands, audit log, architecture and source-rule checks, dependency locking, CI workflow, GPL-3.0 license. Updated the docs for ADR-012 to ADR-016. | `./gradlew clean build` green; 96 unit tests pass; negative tests show the guards work; jar contents inspected. | Real-proxy smoke test denied by the permission system (B-1). Server tests need EULA acceptance (B-2). |
| 2026-11-26 | 2 | Milestone 2.3: world-view memory spike on real 26.3 terrain (609 chunks); OD-3 resolved by ADR-017 (shared position-keyed collision cache, overlay, simulation radius). New never-shipped module `blackdoor-benchmarks`. Found and handled a 26.x save-format change (lowercase `id`/`properties`, string palette entries). | Measured heap per layout, see docs/BENCHMARK_REPORT.md §1. | Nether sample too small (1 chunk); classifier approximate. |
| 2026-11-26 | 2 | Milestone 2.2 (transactions, latency, entity tracker, velocity queue, `packet.transaction`). Owner feedback: GUI rewritten in plain language (Off / Watch / Block, "Protection ON/OFF", plain check names); `/blackdoor player` redesigned as a framed, colour-coded card (ping green/yellow/red, plain finding names, yaw shown -180..180). YAML editor now inserts keys missing from older files. | 159 unit tests pass; live 26.3 client test clean (see §12). | Owner confirmed menu items cannot be taken (to be reconfirmed: answer was ambiguous). |
| 2026-11-26 | 2 | Phase 1 approved. Milestone 2.1: packet engine (7 checks), player state, findings ledger, PacketEvents listener, `checks.yml`. Owner added a requirement mid-session (in-game GUI plus one-command setup); delivered `/blackdoor setup`, `/blackdoor gui`, `/blackdoor checks`, `/blackdoor player`, plus comment-preserving in-game config edits. | Build green; 96 → 143 unit tests (all pass); console smoke tests on Paper 26.3, Folia 26.3, Paper 1.8.8, Spigot 26.3. | Needs a real player session (B-4). |
| 2026-11-26 | 1 | Owner allowed local test servers, accepted the EULA and asked for a push. Smoke-tested 9 configurations (Velocity 4.3.1, BungeeCord, Paper 26.3 / 1.21.11 / 1.20.1 / 1.8.8, Folia 26.3, Spigot 26.3, plus a run without PacketEvents). Fixed legacy PaperSpigot detection. First commit pushed to `github.com/Intruder-tech/black-door`. | §5.1 table; `./gradlew build` green after the fix. | No player connected; Purpur and a Java 17 runtime are untested. |
| 2026-11-26 | 1 | First CI run failed (thread-exit race, test race, OSV noise from compile-only platform APIs). Fixed the executor shutdown so it joins its threads, fixed the reload flag ordering and the test, and split the OSV scan into blocking (shipped) and informational (platform APIs). | CI run 45694262245 green; 8/8 local test reruns green. | GitHub warns that the pinned action versions (checkout@v4, setup-java@v4, ...) use deprecated Node 20; bump them. |

## 8. Approvals

| Date | Phase | Approved by | Notes |
|---|---|---|---|
| 2026-11-26 | 0 | Owner | "Proceeded with Phase I"; decisions chosen via questionnaire |
| 2026-11-26 | 1 | Owner | "approved, proceed with Phase 2" |

## 9. Version Onboarding Log

| Minecraft version | Packet lib version | Physics model | Golden tests | FP corpus | Matrix updated | Date |
|---|---|---|---|---|---|---|
| ≤ 26.3 (version table only) | PacketEvents 2.14.0 | — (Phase 2) | — | — | No | 2026-11-26 |

## 10. Risks Watchlist

| Risk | Severity | Owner action | Status |
|---|---|---|---|
| Cross-version physics maintenance cost | High | Accept scope sequencing in ROADMAP | Open |
| World-view memory at scale (OD-3) | High | Phase 2 spike | Open |
| False positives after game updates | High | Monitor-only defaults + version gating | `VersionSupport` gating implemented |
| Adapters untested on real platforms | High | Unblock B-1/B-2 | **Open, blocking Phase 1 gate** |
| `Bukkit.getPlayer(UUID)` off-thread on Folia assumed safe | Medium | Verify in a Folia smoke test | Open (`[verify]` in code) |
| GPL-3.0 coupling via PacketEvents | — | Resolved: project is GPL-3.0 | Closed |
| BungeeCord legacy forwarding spoofing | Medium | Phase 4 | Mitigation designed |

## 12. Phase 2 Milestones

| # | Milestone | Status | Notes |
|---|---|---|---|
| 2.1 | Packet security engine, player state, findings ledger, PacketEvents listener, `checks.yml`; **plus (owner request) `/blackdoor setup` and the in-game GUI** | **Done 2026-11-26** | See §13 |
| 2.2 | Transaction manager (Ping/Pong and window confirmation), entity tracker, outbound velocity tracking | **Done 2026-11-26** | Heartbeat every 50 ms with reserved ids (ping: `[MIN_INT, MIN_INT+2^24)`, legacy window confirmation: `[-32768, -16385]`); replies to our own pings are consumed; new check `packet.transaction`; entity tracker (cap 512/player); velocity queue tied to acknowledgement. Live test (owner, 26.3 client, ~7 min): 18,036 packets, **ping 2 ms / jitter 1 ms measured**, 125 entities tracked, 0 findings, 0 errors. Missing `transaction` key auto-inserted into an old `checks.yml`. Effect/attribute tracking moved to 2.4 (needed only by physics). Not covered: legacy window-confirmation path with a real 1.8 client; other plugins using overlapping ping ids. |
| 2.3 | World-view memory spike (OD-3) with measured heap numbers | **Done 2026-11-26** | Real 26.3 terrain (609 chunks). Measured per player at view distance 10: block copy 9.99 MiB, collision-shape copy 2.45 MiB, shared cache when players are together 0.08 MiB. Content de-duplication saves only 1%. **Decision (ADR-017):** shared, position-keyed collision cache, per-player overlay, simulation radius 3 chunks (≈0.27 MiB/player worst case), memory budget. Build cost 9.1 µs/section. Full report: docs/BENCHMARK_REPORT.md §1 |
| 2.4a | World view (ADR-017): shared position-keyed collision cache, per-player view and pending-change overlay, memory budget, PacketEvents chunk/block-change translation, coarse collision classifier | **Done 2026-11-26** (live check: 213 chunks, 1.3 of 256 MiB, 1,418 packets, 0 findings) | New module `blackdoor-world`; classes EMPTY/FULL/PARTIAL/FLUID/SPECIAL (PARTIAL/SPECIAL mean "no verdict" until an exact shape registry exists); 1.8-era servers without world names use private per-player namespaces; budget `performance.world-memory-mb` (default 256) |
| 2.4b | Physics core (walk/jump/gravity/friction/collisions/effects/knockback) for 1.8.8, latest 1.21.x, latest 26.x, exact shape registry, golden-trace harness | **Resumed 2026-11-26** — recorder format 2 done (armed by name, starts at rejoin; captures chunks, keys, ticks, effects, attributes, teleport targets; 247 tests; live join checked on 1.21.11). Block shapes done (table of exact collision boxes built from the running server; 35,723 states on 26.3 match the game data, longest build batch 37 ms; `testdata/block-shapes/26.3.txt.gz`). Traces 101, 102, 104 recorded; walking, water and climbing recordings still needed. Height prediction replay done (`blackdoor-physics`, tests only): zero misses on traces 101, 102 and 104 (3,250 matching ticks; trace 102: all 771 airborne ticks judged; about two thirds of those matches are the player not moving vertically). A review found that the client's ground claim and flying packet were trusted; both are now checked. Live-use gaps are in docs/KNOWN_LIMITATIONS.md. Next: horizontal movement. Earlier: step 1 done: golden-trace recorder (`/blackdoor record <player> [seconds]`, JSON lines in `traces/`, max 300 s / 200k events, audited). Three traces recorded, but they lack terrain, sprint/sneak/key input, effects, attributes and teleport destination, so they cannot test physics. On resume: capture terrain from chunk packets (proposed) and re-record | Needs golden traces from real clients (B-5) |

## 13. Milestone 2.1 — What Was Built and Verified

**New modules:** `blackdoor-violations` (findings, bounded ledger, rate-limited alerts),
`blackdoor-state` (per-connection session, movement ring buffer, pending teleports, attachments),
`blackdoor-packets` (engine plus 7 checks). The layering check now covers 14 modules.

**Packet checks** (all default to `monitor`; `mitigate` blocks only when global mode is `enforce`):

| Check | What it catches | Confidence |
|---|---|---|
| `packet.invalid-position` | NaN/infinite coordinates, or beyond the vanilla clamp (±3.0E7 horizontal, ±2.0E7 vertical) | Deterministic |
| `packet.invalid-rotation` | Pitch outside ±90 (with 1e-4 tolerance), non-finite yaw/pitch | Deterministic |
| `packet.invalid-slot` | Held-item slot outside 0..8 | Deterministic |
| `packet.self-interact` | Interacting with your own entity id (only after the login was observed) | Deterministic |
| `packet.teleport-confirm` | Confirming a teleport id never sent (skipped without an observed login or under Via translation) | Deterministic |
| `packet.oversized` | Movement packets > 64 bytes; chat/command/tab-complete text over limits | Deterministic |
| `packet.flood` | Per-connection rates (movement, interact, text, total); at most one finding per second | Heuristic |

**Design points:** runs on each connection's network thread; fails open on internal errors; all
state is bounded (sessions, teleport queue, history, ledger); PacketEvents is registered at LOWEST
priority so a dropped packet reaches other plugins already cancelled; 26.x `ATTACK` packets are
mapped like interact-attack.

**Owner requests delivered:**
- `/blackdoor setup [profile]`: checks the packet layer, version support, online mode / BungeeCord
  forwarding / bind address, config validity, global mode and the audit log. It prints OK / WARN /
  FAIL lines, records the profile, sets `setup.completed: true` and points to `/blackdoor gui`. It
  never weakens anything.
- `/blackdoor gui`: chest-menu control panel. The global mode toggle and every packet check's mode
  cycle on click. Status shows live counters. Reload is a button. Movement/combat, Anti X-Ray/ESP
  and network protection appear as **locked "coming in Phase N"** items, not fake toggles.
  Viewing needs `blackdoor.command.gui`; changing needs `blackdoor.gui.edit`.
- Every in-game change is comment-preserving (only the value is rewritten), validated against the
  schema before writing, written atomically, reloaded all-or-nothing and audited.
- GUI safety: identity-based inventory holder (never title-based); every click and drag in a Black
  Door menu is cancelled at LOWEST and again at HIGHEST priority; rendering runs on the player's
  owning thread (Folia-safe).
- Also new: `/blackdoor checks` and `/blackdoor player <name>`.

**Verification (2026-11-26):**
- Unit tests: violations 5, state 5, packets 17 (including a 20,000-sample property test where
  legitimate movement is never flagged, and fail-open on sink errors), config 32 (including the
  comment-preserving editor), core 37 (setup, settings, GUI pages and clicks, permission gate). All
  pass; full build green.
- Real servers (localhost, console only): Paper 26.3, Folia 26.3, Paper 1.8.8 (Java 21) and
  Spigot 26.3 start with the packet listener attached. `setup smp` passes all checks, sets the
  profile and completes; `checks` and `status` show live counters; `gui` from the console is
  refused correctly; clean shutdown on all four.
- **Not verified yet:** packet inspection of real player traffic, GUI rendering and clicks in a
  real client, the inspection-time budget under load (B-4).

## 14. Phase 4 — Built So Far (2026-11-26)

**Owner play-test (2026-11-26, Paper 26.3, monitor mode):** books, signs and creative items used
normally with no findings; GUI entries confirmed. The owner asked for aligned check icons; the
check page now centres its rows (test in `MenuTest`; new layout not yet confirmed in game).
No modded or hacked client has been used, so blocking of a real attack is unverified.

**4.1 Exploit guards** (new checks in `checks.yml`, all `monitor`, with GUI entries):
`packet.payload` (plugin messages, brand), `packet.book`, `packet.sign`, `packet.creative-item`.
Ids use the `packet.` prefix rather than the planned `exploit.` names in EXPLOIT_PREVENTION.md.

**4.2 Connection protection** (new module `blackdoor-network`, `network.yml`, GUI toggle, status
row): per-address limits for handshakes, status pings and logins; hostname length and login name
validation; private addresses exempt; per-address limits off behind BungeeCord forwarding.

**Verification:**
- `./gradlew build verifyModuleGraph verifySourceRules` green; 187 unit tests, 0 failures.
- Live, copy of Paper 26.3 test server, scripted local probe (60 pings, 21 logins):
  defaults answered all (localhost exempt); with localhost counted, monitor answered all and logged
  findings; mitigate + enforce answered 23 of 60 pings and 0 logins (58 refused).
- The live probe found a defect the unit tests missed: the packet listener never called the
  pre-login handler. Fixed and re-verified. No automated test covers that listener path.
- **Not verified:** exploit guards against a modded or hacked client, proxies, Folia and legacy
  servers with the new code.

**4.3 Proxy connection limits** (`ProxyGate` in core; Velocity ping and pre-login events,
BungeeCord connect and pre-login events). Live probe on copies of both proxies, built-in proxy
throttles off, localhost counted: monitor answered 60 of 60 pings and 20 of 20 logins; mitigate +
enforce answered 29 of 60 pings on both, 1 of 20 logins on Velocity and 0 on BungeeCord.

**Check page icons:** each check now has its own item, Off is gray dye, Block adds a glint (older
servers use stand-in items). Not yet confirmed in game.

**Login system (owner request, 2026-11-26):** new module `blackdoor-auth`, `/signup`, `/signin`,
`auth.yml`, GUI toggle, `/blackdoor auth reset` (docs/AUTHENTICATION.md). 208 unit tests pass;
the server starts with it. **Not verified with a real player:** the join lock, the messages and
that passwords stay out of the server log.

**4.4 Forwarding validation:** `allowed-proxies` blocks connections that bypass the proxy;
`/blackdoor setup` checks Velocity and BungeeCord forwarding and short secrets; per-address limits
switch off behind Velocity modern forwarding too. 215 unit tests. Live on a Paper 26.3 copy: a
direct connection was refused, a listed address got 60 of 60 pings, a short secret was reported.
**Not verified:** with a real proxy in front; BungeeGuard tokens are not implemented.
The owner confirmed the login system works in game; the server log held no password lines.

**4.5 creative guards:** `packet.item-data` (depth and element limits on creative items) and
`packet.game-mode` (creative action outside creative mode), each with a GUI item. Diagnostics and
the GUI Status item show how many packets could not be decoded. 226 unit tests. Owner play test on
Paper 26.3 with creative and survival use: 19,833 packets, 0 unreadable, 0 findings.

**4.6 scripted-client test (Paper 1.21.11, offline mode, localhost):** found that over-limit sign,
book, payload and chat packets were never reported (PacketEvents rejects them while decoding), and
that flood mitigation dropped keep-alive replies and disconnected the player. Both fixed; all 17
cases pass with checks on Watch and again on Block. 230 unit tests.
**Not verified:** the scripted client on 26.3 (the library stops at 26.1), or a real hacked client.

**4.7 adaptive mode:** during a flood of logins from many addresses, addresses that played before
keep getting in and unknown ones share a small allowance (`network.yml` `adaptive`, GUI "Bot flood
mode"). Nothing is refused for `warmup-seconds` (300) after start. 246 unit tests. Live check on Paper 1.21.11 (offline mode, loopback source addresses):
attack mode started, strangers were mostly refused, the known address got in, attack mode ended.
**Not verified:** a real proxy, a distributed flood from real addresses, or 26.3.

**4.8 wrap-up:** anti-xray status of Paper in setup and the GUI (Black Door hides no ores itself);
spoof and flood tests with a real Velocity and BungeeCord on this PC (direct and forged joins
refused, proxy joins work, bot flood mode admits a known address); connection guard measured at
about 0.5 us per connection (docs/BENCHMARK_REPORT.md section 2).

**Phase 4 complete** against docs/ROADMAP.md. Quarantine belongs to Phase 5 and the edge agent to
"Later / Optional". Not verified: real online-mode accounts through a proxy, a distributed attack
from real addresses. See docs/KNOWN_LIMITATIONS.md.

**4.9 anti-xray switch (after Phase 4, owner's request):** the GUI item now turns Paper's
anti-xray on or off (`core/antixray/AntiXrayFiles`: comment-preserving edit, backup once, atomic
write, restore on a failed write; shows configured and running state; audited). The running state
survives a plugin re-enable (`StartupSnapshot`, tagged with the JVM start time); setup uses the
same logic. Also fixed: `YamlLineEditor` keeps each line's own ending in files that mix CRLF and
LF. Unit tests only.
**Not verified:** on a running Paper server (in game, and Paper reading the edited files after a
restart).

## 11. Next Steps

0. Owner to review and commit 4.7 (adaptive mode) and check the new GUI layout in game.
1. Phase 4 candidates, owner's choice: bot scoring and quarantine, anti-xray, edge agent.
2. Milestone 2.4b: physics core (walk, jump, gravity, friction, collisions, effects, knockback) for
   1.8.8, latest 1.21.x and latest 26.x. Golden traces from real clients are needed (B-5).
3. GUI refinements: deferred by the owner until the backend is complete.
4. CI upkeep: bump GitHub Actions to Node-24 versions; add a CycloneDX SBOM and a Java 17 runtime job.
