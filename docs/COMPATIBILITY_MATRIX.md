# Compatibility Matrix

> **Phase:** 0 · **Last updated:** 2026-11-26
> **Nothing below is supported yet.** Every entry is a *target*. An entry changes to
> `supported` only when its integration test suite passes in CI; the machine-readable source of
> truth is [compatibility-matrix.yml](compatibility-matrix.yml).

## 1. Status Legend

| Status | Meaning |
|---|---|
| `planned` | Target, not implemented. |
| `partial` | Works with documented gaps. |
| `supported` | Integration tests pass in CI on the listed versions. |
| `best-effort` | Expected to work; not in the CI matrix; bug reports accepted. |
| `unsupported` | Not supported; reason given. |

## 2. Server Platforms

| Platform | Target versions | JVM | Adapter | Target status | Notes |
|---|---|---|---|---|---|
| Spigot | 1.8.8 → latest | 17+ (ADR-002) | platform-bukkit | planned | Spigot diverges from Paper since ~1.21.4 hard fork. |
| Bukkit (CraftBukkit) | 1.8.8 → latest | 17+ | platform-bukkit | best-effort | Rarely deployed directly. |
| Paper | 1.8.8 → latest | 17+ | platform-bukkit (< 1.20), platform-paper (≥ 1.20) | planned | Legacy Paper builds are unsupported upstream; tested only for representative versions. |
| Purpur | Versions tracking Paper ≥ 1.20 | 21+/25 | platform-paper | planned | Purpur adds movement features (e.g., configurable mechanics) that require exemptions. |
| Folia | ≥ 1.20 (Paper scheduler API) | 21+/25 | platform-paper | planned | Requires `folia-supported: true`; region-thread rules enforced. |
| 1.8.8 forks (e.g., maintained PvP forks) | 1.8.8 | 17+ | platform-bukkit | best-effort | Must run on Java 17. Per-fork testing on request. |
| Fabric / Forge / NeoForge servers | — | — | — | unsupported | Not in scope for Phase 0–8. Modded physics invalidate vanilla prediction. |
| Sponge | — | — | — | unsupported | Not in scope. |

## 3. Proxy Platforms

| Platform | Target | Status | Notes |
|---|---|---|---|
| Velocity | 4.x (built against API 4.3.1; requires Java 25) | planned | Preferred. Modern forwarding (HMAC) validation. Velocity 3.x is not targeted. |
| BungeeCord | Current | planned | Legacy forwarding is unauthenticated; requires network isolation or token-based guard. |
| Waterfall | Last release | best-effort | End-of-life upstream. |

## 4. Protocol Translation & Bedrock

| Integration | Target | Status | Behavior |
|---|---|---|---|
| ViaVersion | Current | planned | Client version read from Via; physics uses client version. |
| ViaBackwards | Current | planned | Same. |
| ViaRewind | Current | planned | 1.7/1.8 clients on newer servers; physics uses legacy client model, combat rules use server version. |
| Geyser + Floodgate | Current | planned | Bedrock players identified; Java movement/combat prediction checks **disabled** for them by default; protocol & exploit guards apply. |

## 5. Representative Test Versions (proposal — OD-5)

| Era | Versions in CI | Why |
|---|---|---|
| Legacy PvP | 1.8.8 | Largest legacy population; distinct combat. |
| Pre-flattening | 1.12.2 | Last pre-1.13; distinct block IDs. |
| Post-flattening | 1.16.5 | Last Java-8-era major; nether update physics. |
| Transaction switch | 1.17.1 | Ping/Pong introduced. |
| Mid | 1.18.2, 1.19.4 | World height change; signed chat. |
| Config state | 1.20.1, 1.20.4 | Before/after configuration phase. |
| Components | 1.20.6, 1.21.1 | Item components. |
| Input packet | 1.21.4, latest 1.21.x | Directional input packet; Paper hard fork. |
| Year-based | latest 26.x | Java 25 baseline. |

Intermediate versions are `best-effort` until added to CI.

## 6. Distribution Artifacts (planned)

| Artifact | Contents | Targets |
|---|---|---|
| `BlackDoor-Universal-<ver>.jar` | Universal Bukkit-family jar (Bukkit adapter + Paper adapter, runtime-selected) | Spigot, Paper, Purpur, Folia |
| `BlackDoor-Velocity-<ver>.jar` | Proxy subset (built by `blackdoor-proxy-velocity`, ADR-015) | Velocity 4.x |
| `BlackDoor-Bungee-<ver>.jar` | Proxy subset (built by `blackdoor-proxy-bungee`, ADR-015) | BungeeCord, Waterfall |

**Build status (2026-11-26):** all three jars compile and package; unit tests pass. **None has
been started on a real server or proxy yet**, so every row above remains `planned`.
| `blackdoor-edge` (optional) | Separate service, not a plugin | Linux hosts with nftables |

A second Bukkit artifact will be introduced only if a single universal jar proves infeasible
(e.g., conflicting descriptor requirements between legacy and modern loaders).
