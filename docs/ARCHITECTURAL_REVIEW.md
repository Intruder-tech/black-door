# Architectural Review & Feasibility Assessment

> **Phase:** 0 · **Status:** Draft for review · **Last updated:** 2026-11-26

This document records the Phase 0 research, the feasibility assessment, the evaluation of the
proposed module layout, the dependency evaluation, and the architectural decision log (ADRs).
Facts marked **[verify]** must be re-confirmed at Phase 1 kickoff against primary sources
(Mojang changelogs, PaperMC/Spigot/Velocity source, PacketEvents and ViaVersion registries).

---

## 1. Research Findings

### 1.1 Ecosystem state (as of 2026-11)

| Topic | Finding | Consequence for Black Door |
|---|---|---|
| Minecraft versioning | Mojang moved from `1.21.x` to year-based numbering. `26.1` followed the 1.21 line; `26.2` released June 2026; `26.3` has reached release-candidate stage and PaperMC has published a 26.3 news post. **[verify]** exact current patch. | "Latest stable" is a moving target; pinned at Phase 1 kickoff and re-pinned per release. Version logic must never assume `1.x.y` strings. |
| Java runtime | 26.1 is the first version requiring **Java 25**. 1.20.5–1.21.x require Java 21; 1.18–1.20.4 require Java 17; 1.8.8-era servers traditionally run Java 8. | A single plugin jar compiled to Java 17 bytecode runs on Java 17/21/25 JVMs. Java-8-only legacy servers are a distinct problem (ADR-002). |
| Paper hard fork | Paper hard-forked from Spigot starting around 1.21.4. Plugins compiled against Paper-API may not run on Spigot. | Spigot and Paper need separate platform adapters; the Paper adapter must not leak into the Spigot path. |
| Unobfuscated server jars | From 26.1, Spigot ships unobfuscated server jars and Paper dropped its internal remapper. **[verify]** | Any NMS access (avoided by design) would break across this boundary. Reinforces "no NMS in core". |
| Folia | Region-threaded Paper fork. Plugins must declare `folia-supported: true`. No single main thread; legacy Bukkit scheduler is unsupported. Paper exposes the same region/entity/global schedulers (since 1.20), so one code path runs on both. | Paper and Folia adapters can be merged (ADR-006). |
| Waterfall | PaperMC ended Waterfall development (announced 2024). | BungeeCord adapter targets BungeeCord API; Waterfall is "legacy, best-effort". |
| PacketEvents | Active. 2.12.x adds 26.1/26.2 support; covers 1.8 → 26.x on Spigot/Paper/Folia, Velocity, BungeeCord, Fabric. Licensed **GPL-3.0**. | Strong candidate for the packet abstraction (ADR-003) — with license consequences (ADR-004). |
| Grim (GrimAC) | Open-source, GPL-3.0, prediction-based anti-cheat supporting roughly 1.8 → modern on Spigot/Paper/Folia; requires Java 17. Uses packet-derived world replica and transaction-based latency compensation. | Validates feasibility of the prediction approach on a Java 17 baseline across 1.8 → modern. **Concepts are studied; code is not copied** (clean-room policy, §4). |
| ViaVersion family | ViaVersion (newer clients on older servers), ViaBackwards (older clients on newer servers), ViaRewind (1.7/1.8 clients on newer servers). | Client protocol ≠ server version is a normal, first-class case. Physics follows the **client's** movement code; world and combat rules follow the **server's** version. |
| Geyser/Floodgate | Bedrock clients join through Geyser, which translates Bedrock movement into Java packets. | Java physics prediction is not valid for Bedrock players. They get protocol-level and exploit checks only by default. |

### 1.2 Protocol milestones that shape the design

Exact protocol numbers are **not** hand-maintained; they come from the packet library / Via
registries at build time. Milestones below drive branching logic and are **[verify]** items.

| Milestone | Approx. version | Design impact |
|---|---|---|
| Idle "flying" packet every tick | ≤ 1.8 | Timer detection can count ticks directly. |
| Idle flying packet removed; 1.9 combat (cooldown, offhand, elytra) | 1.9 | Timer must use transaction-anchored balance; combat rules diverge. |
| Small-movement suppression ("0.03 threshold") | 1.9+ (behavior changed in later versions) **[verify]** | Prediction must tolerate missing position updates. |
| Flattening (numeric IDs → block states), swimming | 1.13 | World replica needs per-version block-state mapping; collision shapes differ. |
| Ping/Pong play packets (replacing window confirmation as transaction) | 1.17 | Transaction mechanism switches packet type. |
| Signed chat | 1.19 | Chat validation must respect signing; never rewrite signed messages. |
| Configuration connection state | 1.20.2 | Connection state machine gains a state; validation tables per state. |
| Item data components replace item NBT | 1.20.5 | Item validators need two separate implementations. |
| Player input packet carries directional key state | 1.21.2 **[verify]** | Movement inputs become directly observable → much tighter prediction. |
| Year-based versions, Java 25 | 26.1 | Version parsing; CI toolchain must provide Java 25 for test servers. |

### 1.3 Threading model of the target platforms

| Platform | Inbound packet thread | Game logic thread(s) | Safe API access |
|---|---|---|---|
| Spigot / Paper (non-Folia) | Netty event loop (per channel) | Single main thread | Bukkit world/entity APIs only on main thread |
| Folia | Netty event loop | Region threads; entity owned by its region | Entity APIs only on the owning region thread (entity scheduler) |
| Velocity | Netty event loop | Event executor pool | API designed to be thread-safe |
| BungeeCord | Netty event loop | Event threads | Most API thread-safe; scheduler for timed work |

**Conclusion:** the only thread that sees every packet of a player in order without locking is
that player's Netty event loop. The detection hot path runs there, on packet-derived state, and
never calls platform world APIs.

### 1.4 Legitimate causes of false positives (summary)

Latency and jitter; packet loss and TCP batching; server lag (low TPS / high MSPT); teleports,
respawns, world changes; server-applied velocity (knockback, explosions, plugins); vehicles;
custom items/enchantments/attribute modifiers; movement plugins (launch pads, grapples, boosts);
block changes the client has not yet received; protocol translation (Via*); Bedrock (Geyser);
legitimate client mods that alter packet cadence; NPC plugins sending fake entities.
Full treatment: [FALSE_POSITIVE_POLICY.md](FALSE_POSITIVE_POLICY.md).

---

## 2. Technical Feasibility Assessment

**High** = proven by existing open-source systems; **Medium** = feasible with significant
per-version engineering; **Low** = fundamentally limited by what the server can observe.

| Capability | Feasibility | Rationale / main risk |
|---|---|---|
| Packet validation (state, size, rate, bounds) | **High** | Deterministic rules. Risk: breaking protocol translators or plugins with custom payloads. |
| Packet-derived player state | **High** | Proven by prediction anti-cheats. Risk: drift → must resync on teleport/respawn. |
| Movement prediction 1.8 → latest | **Medium** | Feasible, but per-version physics and collision data are large and change each drop. Largest maintenance cost. |
| Combat reach/hitbox with latency compensation | **High / Medium** | Entity position history + transaction anchoring. Risk: interpolation differences per version. |
| Rotation-based aim heuristics | **Medium / Low** | Statistical; strong cheats mimic humans. Staff-review grade only. |
| Autoclicker / macro detection | **Low** | Click timing cannot separate skilled players, macros and hardware. Probabilistic; never auto-punish. |
| World interaction (Scaffold, FastBreak, Nuker) | **High / Medium** | Geometry and timing are deterministic; break-speed formula per version. |
| Inventory checks | **Medium** | Server-authoritative inventory exists; inventory-while-moving is hard because modern clients do not announce opening the player inventory. |
| Information cheats (X-ray, ESP) | **Prevention: Medium · Detection: Low** | Prevent by not sending information. Detection only via statistical patterns. |
| Exploit prevention | **High** | Mostly bounds checks; must stay current per version. |
| App-layer connection/login/ping throttling | **High** | Standard rate limiting; limited because TCP is already accepted. |
| Volumetric DDoS mitigation | **Not feasible in-plugin** | Requires upstream filtering. Optional edge agent can push bounded firewall rules (opt-in). |
| Proxy forwarding validation | **High (Velocity modern) / Medium (Bungee legacy)** | Velocity modern forwarding is HMAC-authenticated; BungeeCord legacy forwarding is unauthenticated and needs network isolation or a token scheme. |
| Folia support | **Medium** | Thread ownership must be explicit everywhere; Netty-thread design fits naturally. |
| Bedrock (Geyser) movement checks | **Low** | Translated movement does not follow Java physics. |
| ML-based behavior analysis | **Deferred** | Needs labeled datasets that do not exist yet. Optional; adopted only on measured improvement. |

**Overall verdict:** feasible as a multi-year effort if scope is sequenced. Highest risks:
(1) cross-version physics maintenance, (2) world-replica memory at high player counts,
(3) false-positive control. These drive phase gating in [ROADMAP.md](ROADMAP.md).

---

## 3. Evaluation of the Proposed Module Structure

| Proposed module | Decision | Reason |
|---|---|---|
| `blackdoor-api` | **Keep** | Public, versioned, zero third-party dependencies. |
| `blackdoor-core` | **Keep (narrowed)** | Composition root: lifecycle, wiring, command logic. No domain logic. |
| `blackdoor-protocol` | **Keep** | Version model + Black Door's own packet abstraction (interfaces). |
| `blackdoor-packets` | **Keep** | Packet security engine (validation pipeline, rate/size/order rules). |
| — | **Add `blackdoor-protocol-packetevents`** | Isolates the third-party packet library so it can be replaced. |
| — | **Add `blackdoor-common`** | Bounded buffers, rate limiters, clocks, math. Avoids duplication and cycles. |
| — | **Add `blackdoor-state`** | Player state engine; required as its own layer by the separation rules. |
| — | **Add `blackdoor-world`** | Packet-derived world replica + collision shapes. Largest memory consumer; own budget. |
| `blackdoor-physics` | **Keep** | Version-aware movement simulation. |
| `blackdoor-detection` | **Keep** | Check implementations + check runner. |
| `blackdoor-behavior` | **Keep** | Statistical analysis, off the hot path. |
| — | **Add `blackdoor-violations`** | Ledger, decay, correlation, evidence — separated from enforcement as required. |
| `blackdoor-enforcement` | **Keep** | Policy → action mapping, cooldowns, platform action requests. |
| — | **Add `blackdoor-config`** | Schema, validation, migration, atomic reload. Needed by all modules; cannot live in core (cycle). |
| `blackdoor-network` | **Keep** | Platform-agnostic connection/login/ping protection. |
| `blackdoor-exploits` | **Keep** | Version-specific exploit guards. |
| `blackdoor-storage` | **Keep** | SQLite / PostgreSQL / MariaDB-MySQL / Redis backends. |
| `blackdoor-telemetry` | **Keep** | Metrics, structured logs, alerts, webhooks. |
| `blackdoor-platform-bukkit` | **Keep** | Bukkit/Spigot adapter incl. legacy 1.8.8. |
| `blackdoor-platform-paper` + `blackdoor-platform-folia` | **Merge → `blackdoor-platform-paper`** | Paper's schedulers exist on Paper ≥ 1.20 and Folia. One runtime-detected code path. Paper < 1.20 uses the Bukkit adapter. |
| `blackdoor-proxy-velocity` | **Keep** | |
| `blackdoor-proxy-bungee` | **Keep** | BungeeCord; Waterfall best-effort. |
| `blackdoor-integrations` | **Keep** | Optional soft-dependency bridges (Via*, Geyser/Floodgate, anti-xray providers). Loaded only when target present. |
| `blackdoor-testing` | **Keep** | Fixtures, simulated clients, packet builders, replay harness, fuzz targets. |
| — | **Add `blackdoor-benchmarks`** | JMH; never shipped. |
| — | **Add `blackdoor-edge` (optional, late)** | Separate privileged process for firewall integration; not part of the plugin. |
| — | **Add `dist-*` modules** | Shading/relocation per artifact: `dist-bukkit` (Bukkit/Spigot/Paper/Purpur/Folia), `dist-velocity`, `dist-bungee`. |

Resulting dependency graph: [ARCHITECTURE.md §3](ARCHITECTURE.md). It is acyclic and enforced by
a build check (Phase 1 acceptance criterion).

---

## 4. Review of Existing Anti-Cheat Architectures (concepts only)

| Project | License | Concepts studied | Not adopting |
|---|---|---|---|
| Grim | GPL-3.0 | Prediction by enumerating possible inputs; transaction-anchored latency compensation; packet-derived world; Netty-thread processing. | Code. Tight check/engine coupling. |
| NoCheatPlus (and forks) | GPL-3.0 | Long FP-handling history; per-check exemptions; check registry. | Threshold-heavy movement model; main-thread Bukkit-event processing. |
| Proprietary (Vulcan, Polar, Intave, …) | Proprietary | Public descriptions only: punishment pipelines, alert UX. | Anything not public. |
| Paper anti-xray / Orebfuscator | GPL-3.0 **[verify]** | Prevention by obfuscating ore data in chunk packets. | Re-implementing; integrate instead. |

**Clean-room policy:** contributors may read public documentation and design write-ups. Code from
other projects must not be copied or translated. Even if GPL-3.0 is adopted (ADR-004), the project
prefers independent implementations so design choices are documented and tested here.

---

## 5. Dependency Evaluation

Principles: minimal set; every shaded runtime dependency **relocated** under the project package
root (OD-4); versions pinned with Gradle dependency locking + checksum verification; licenses
compatible with the project license.

### 5.1 Runtime dependencies

| Dependency | Purpose | License | Decision | Notes |
|---|---|---|---|---|
| PacketEvents | Cross-version packets, Netty injection, client-version detection (incl. Via) | GPL-3.0 | **Adopt behind adapter** | Shaded vs. separate plugin: OD-6. |
| Spigot / Paper / Velocity / BungeeCord APIs | Platform integration | Provided | `compileOnly` | Never shaded. |
| SnakeYAML 2.x | YAML parsing for all config files | Apache-2.0 | **Adopted (Phase 1, ADR-012)** | Shaded + relocated. Loaded only through `SafeConstructor` with alias, depth, size and duplicate-key limits. Replaces the earlier Configurate proposal. |
| Configurate (YAML) | Typed config | Apache-2.0 | **Rejected in Phase 1 (ADR-012)** | Adds a layer over SnakeYAML without preserving comments on rewrite; our own typed reader gives explicit validation with fewer dependencies. |
| HikariCP | JDBC pooling | Apache-2.0 | **Adopt** | Loaded only when a SQL backend is configured. |
| sqlite-jdbc (Xerial) | SQLite driver | Apache-2.0 | **Prefer server-provided** | Bukkit-family servers bundle it; otherwise library-loaded. Contains native libraries. |
| PostgreSQL JDBC | PostgreSQL driver | BSD-2-Clause | **Optional** | Library-loaded, not shaded by default. |
| MariaDB Connector/J | MySQL/MariaDB driver | LGPL-2.1 | **Optional** | Preferred over MySQL Connector/J (GPL-2.0 + FOSS exception). |
| Lettuce or Jedis | Redis/Valkey | Apache-2.0 / MIT | **Defer (Phase 6)** | Only if distributed state is required. |
| Adventure / MiniMessage | Messages | MIT | **Platform-provided where available** | Paper and Velocity bundle it; Spigot/Bungee use shaded `adventure-platform`. |
| fastutil | Primitive collections | Apache-2.0 | **Evaluate Phase 2** | Shade a minimized subset only if benchmarks justify. |
| Caffeine | Bounded caches | Apache-2.0 | **Evaluate Phase 4** | A small in-house bounded LRU may suffice. |
| Prometheus client | Metrics exposition | Apache-2.0 | **Avoid** | Text format is trivial to emit. |
| bStats | Usage statistics | MIT | **Reject** | Conflicts with "no hidden telemetry". |
| Gson | JSON | Apache-2.0 | **Platform-provided** | Bundled by all target platforms. Webhooks/evidence export only. |

### 5.2 Build and test dependencies (never shipped)

| Dependency | Purpose | License |
|---|---|---|
| Gradle (Kotlin DSL) + Shadow plugin | Build, shading, relocation | Apache-2.0 |
| JUnit 5, AssertJ | Unit tests | EPL-2.0 / Apache-2.0 |
| jqwik | Property-based tests | EPL-2.0 |
| Jazzer | Coverage-guided fuzzing | Apache-2.0 |
| JMH | Microbenchmarks | GPL-2.0 + Classpath Exception |
| Testcontainers | DB/Redis integration tests | MIT |
| MockBukkit | Bukkit API unit tests (limited version coverage) | MIT |
| Real server jars (Paper, Spigot via BuildTools, Folia, Velocity, BungeeCord) | Integration tests | Downloaded/built in CI, never redistributed |
| Headless protocol client (MCProtocolLib, MIT, or in-house) | Scripted clients for integration, FP and load tests | MIT |
| osv-scanner / OWASP Dependency-Check; CycloneDX | Vulnerability scan, SBOM | Apache-2.0 |

---

## 6. Architectural Decision Log

Status of all ADRs: **Proposed** until owner approval.

**ADR-001 — Language and build.** Java (no Kotlin runtime), Gradle Kotlin DSL multi-module.
Minimal runtime footprint; ecosystem norm.

**ADR-002 — Java 17 bytecode baseline.** All plugin modules target Java 17. Legacy servers
(1.8.8 → 1.16.5) are supported **only when run on a Java 17+ JVM**. No Java 8 artifact.
Consequence: Java-8-only servers unsupported; some 1.8.8 builds need a maintained fork or JVM
flags — documented and integration-tested per build. Open: OD-1.

**ADR-003 — PacketEvents behind an internal interface.** Options were own Netty codecs for
every version (huge continuous maintenance, correctness risk), ProtocolLib (Bukkit-only, no
proxies), or PacketEvents (all required platforms, Via-aware). Chosen: PacketEvents wrapped by
`blackdoor-protocol`; checks never import its types. Upgrades are pinned, reviewed and fuzzed.

**ADR-004 — Project license (open).** PacketEvents is GPL-3.0; distributing with it makes a
combined work. Recommendation: **GPL-3.0** — compatible, auditable, matches "no backdoors".
Alternative (proprietary/permissive) requires dropping PacketEvents and owning all codecs. OD-2.

**ADR-005 — Detection on the player's Netty event loop.** Per-packet validation, state, physics
and checks run on the player's channel event loop against packet-derived state only. Strict
ordering without locks; Folia-safe; zero main-thread cost. Slow work goes to bounded async queues.
Each check has a time budget and a circuit breaker.

**ADR-006 — Merge Paper and Folia adapters.** One module using Paper's entity/region/global
schedulers; Folia detected at runtime; `folia-supported: true` declared.

**ADR-007 — Packet-derived world replica.** Per-player compact collision view built from outbound
chunk/block packets, transaction-anchored. Memory risk evaluated in a Phase 2 spike
(collision-only palettes, bounded simulation radius, shared immutable sections + per-player
overlays). OD-3.

**ADR-008 — Transaction-anchored latency compensation.** Every security-relevant server→client
change (velocity, teleport, block change, effect, attribute, entity movement) is bracketed by a
transaction (Ping/Pong on 1.17+, window confirmation before). Checks accept both pre- and
post-change possibilities until acknowledged. Reserved ID range to avoid colliding with other
plugins; verified by integration tests.

**ADR-009 — Monitoring-only defaults.** All checks default to monitor mode. Packet/exploit guards
that only drop packets no legitimate client can produce are on by default; each documents why.

**ADR-010 — No server internals in core paths.** Core modules never reference NMS/CraftBukkit.
Adapter-level internals, if ever needed, are isolated, version-gated and optional.

**ADR-011 — Firewall integration is out-of-process and opt-in.** The plugin never executes shell
commands. The optional `blackdoor-edge` agent accepts authenticated, typed requests over a local
socket and only adds time-bounded entries to a pre-created nftables set.

**ADR-012 — SnakeYAML directly, not Configurate (Phase 1).** One shaded, relocated dependency.
`SafeConstructor` blocks arbitrary class construction; `LoaderOptions` caps aliases (20), nesting
(32), size (1 MiB) and rejects duplicate keys. Typed validation is done by `ConfigReader`, which
collects every error with its key path. Migrations rewrite the file (comments are lost), so a
backup `<file>.bak-v<old>` is always written first.

**ADR-013 — Build toolchain JDK 25, bytecode per module.** Velocity API 4.x ships Java 25
bytecode, so `blackdoor-proxy-velocity` targets Java 25. Every other module targets Java 17
(ADR-002). One JDK 25 toolchain (auto-provisioned by Gradle) compiles everything with `--release`.

**ADR-014 — PacketEvents is a soft dependency on Bukkit-family servers (refines OD-6).** If it is
missing, Black Door still starts, reports **DEGRADED** with the reason, and keeps commands and
diagnostics available. A hard `depend` would stop the plugin loading at all, which hides the
problem from operators who only see a missing plugin.

**ADR-015 — Proxy jars are shaded in their adapter modules.** `dist-velocity` and `dist-bungee`
were not created: each proxy adapter has exactly one artifact, so a separate module adds nothing.
`dist-bukkit` remains because it merges two adapters (Bukkit and Paper/Folia).

**ADR-016 — Proxy command label `blackdoorproxy`.** A proxy intercepts commands before the
backend sees them, so the proxy must not register `/blackdoor`.

**ADR-017 — World view: shared, position-keyed collision cache (resolves OD-3).** Measured on
real 26.3 terrain (BENCHMARK_REPORT.md §1): a per-player block copy costs ~10 MiB/player and a
per-player collision-shape copy ~2.45 MiB/player at view distance 10, too much at 500 players.
Content de-duplication saves ~1%, because sharing comes from players viewing the same chunks.
Design: one collision-shape section per (dimension, chunk, section) shared by all players, a
per-player overlay of unacknowledged block changes, a bounded simulation radius (default 3
chunks), reference counting and eviction, and a hard memory budget that degrades to "no verdict".

**Decision status (2026-11-26):** ADR-001 to ADR-017 accepted. OD-1, OD-2, OD-4, OD-5 and OD-6
were resolved by the owner (§7).

---

## 7. Open Decisions

| ID | Decision | Outcome | Status |
|---|---|---|---|
| OD-1 | Java 17 minimum runtime (drops Java-8-only 1.8.8 servers) | Accepted (Velocity needs Java 25 — ADR-013) | **Resolved 2026-11-26** |
| OD-2 | Project license | **GPL-3.0** (LICENSE file added) | **Resolved 2026-11-26** |
| OD-3 | World replica memory strategy | Shared position-keyed collision cache + per-player overlay + simulation radius (ADR-017; measurements in BENCHMARK_REPORT.md §1) | **Resolved 2026-11-26** |
| OD-4 | Java package root / Maven group | Group `io.github.intruder-tech`, package `io.github.intruder_tech.blackdoor` (hyphen is illegal in Java package names) | **Resolved 2026-11-26** |
| OD-5 | Tested version set | Representative subset (1.8.8, 1.12.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6, 1.21.1, 1.21.4, latest 1.21.x, latest 26.x); intermediates "expected, untested" | **Resolved 2026-11-26** |
| OD-6 | PacketEvents shaded vs. separate plugin | Separate plugin, soft dependency (ADR-014) | **Resolved 2026-11-26** |
| OD-7 | Redis/Valkey at launch? | Defer to Phase 6 | — |
| OD-8 | Own entity-hiding (anti-ESP) vs. integrating existing tools | Integrate first; own version experimental later | — |
| OD-9 | Name "Black Door" resembles "backdoor"; host scanners/admins may react | Owner awareness | — |
