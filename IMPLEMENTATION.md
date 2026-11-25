# Black Door — Implementation Tracker

> The single place to see where the project stands. Updated at the end of every work session
> and at every phase gate. Reports what was done, what was verified, what changed, what is
> blocked, and what needs the owner's decision.

**Last updated:** 2026-11-26
**Current phase:** Phase 1 — Foundation
**Phase status:** **Code complete, smoke-tested on 9 real server/proxy configurations (§5.1),
CI green on GitHub — awaiting owner approval.** Known gaps: no player connected during tests,
Purpur and a Java 17 runtime untested, SBOM not added yet.
**Protections active:** None. Phase 1 contains no security features by design.
**Next gate:** Owner approves Phase 1, then Phase 2 starts.

---

## 1. Status Dashboard

| Phase | Name | Status | Started | Gate passed |
|---|---|---|---|---|
| 0 | Research, feasibility, threat model, architecture | **Done** | 2026-11-26 | 2026-11-26 (owner: "Proceeded with Phase I") |
| 1 | Core architecture, build, adapters, protocol abstraction, config | **In progress — awaiting verification** | 2026-11-26 | — |
| 2 | Packet inspection, player state, physics | Not started | — | — |
| 3 | Combat / movement / inventory / interaction detections | Not started | — | — |
| 4 | Exploits, app-layer network protection, proxies | Not started | — | — |
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
| 2026-11-26 | 1 | Owner allowed local test servers, accepted the EULA and asked for a push. Smoke-tested 9 configurations (Velocity 4.3.1, BungeeCord, Paper 26.3 / 1.21.11 / 1.20.1 / 1.8.8, Folia 26.3, Spigot 26.3, plus a run without PacketEvents). Fixed legacy PaperSpigot detection. First commit pushed to `github.com/Intruder-tech/black-door`. | §5.1 table; `./gradlew build` green after the fix. | No player connected; Purpur and a Java 17 runtime are untested. |
| 2026-11-26 | 1 | First CI run failed (thread-exit race, test race, OSV noise from compile-only platform APIs). Fixed the executor shutdown so it joins its threads, fixed the reload flag ordering and the test, and split the OSV scan into blocking (shipped) and informational (platform APIs). | CI run 45694262245 green; 8/8 local test reruns green. | GitHub warns that the pinned action versions (checkout@v4, setup-java@v4, ...) use deprecated Node 20; bump them. |

## 8. Approvals

| Date | Phase | Approved by | Notes |
|---|---|---|---|
| 2026-11-26 | 0 | Owner | "Proceeded with Phase I"; decisions chosen via questionnaire |
| — | 1 | — | Pending B-1/B-2 verification |

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

## 11. Next Steps

1. Owner reviews and approves Phase 1 (all blockers resolved; remaining gaps listed in §5).
2. CI upkeep: bump GitHub Actions to Node-24 versions; add a CycloneDX SBOM and a Java 17
   runtime test job.
3. Smoke test with a real player connected (exercises `runForPlayer` on Folia), and on Purpur.
4. On Phase 1 approval: start Phase 2 with the world-view memory spike (OD-3) and the packet
   validation pipeline.
