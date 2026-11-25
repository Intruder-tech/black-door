# Black Door — Architecture

> **Phase:** 0 · **Status:** Proposed design (not implemented) · **Last updated:** 2026-11-26
> Decisions referenced as ADR-nnn are in [ARCHITECTURAL_REVIEW.md §6](ARCHITECTURAL_REVIEW.md).

## 1. System Context

```text
                 ┌────────────────────── upstream (out of scope) ──────────────────────┐
 Players ──TCP──►│ Provider DDoS filtering / L3-L4 scrubbing / host firewall (nftables)│
                 └──────────────────────────────┬───────────────────────────────────────┘
                                                │
                        ┌───────────────────────▼────────────────────────┐
                        │ Proxy (optional): Velocity / BungeeCord        │
                        │  └ blackdoor-proxy-*: connection/login/ping    │
                        │    throttling, forwarding validation, events   │
                        └───────────────────────┬────────────────────────┘
                              forwarded identity│(Velocity: HMAC-signed)
                        ┌───────────────────────▼────────────────────────┐
                        │ Backend: Spigot / Paper / Purpur / Folia       │
                        │  └ blackdoor (dist-bukkit): packet security,   │
                        │    state, physics, checks, exploits, enforcement│
                        └───────┬───────────────┬───────────────┬────────┘
                                │               │               │
                       SQLite / PostgreSQL   Staff (commands,  Optional: webhooks,
                       / MariaDB / Redis     alerts)           Prometheus endpoint,
                                                               blackdoor-edge agent
```

## 2. Layered Design

```text
 ┌──────────────────────────── Platform adapters ─────────────────────────────┐
 │ platform-bukkit │ platform-paper (Paper + Folia) │ proxy-velocity │ proxy-bungee │
 └───────────────┬────────────────────────────────────────────────────────────┘
                 │ implements Platform SPI (scheduler, players, actions, events)
 ┌───────────────▼──────────── Composition root: core ────────────────────────┐
 │ lifecycle · module wiring · commands (platform-neutral) · health            │
 └──┬───────────┬────────────┬────────────┬───────────┬────────────┬──────────┘
    │           │            │            │           │            │
 network    packets ──► state ──► world ──► physics ──► detection ──► violations ──► enforcement
 (pre-login) (validate)  (model)  (replica)  (simulate)  (checks)     (ledger)        (actions)
    │           │                                         │              │
    │        exploits ─────────────────────────────────────┘              │
    │                                     behavior (async) ◄──────────────┤
    └──────────────► telemetry ◄──────────── storage (async) ◄────────────┘
                 config (used by all) · common (used by all) · api (public)
```

### 2.1 Module responsibilities and allowed dependencies

| Module | Responsibility | May depend on |
|---|---|---|
| `blackdoor-api` | Public interfaces, events, value types. Stable, semver'd. | — (JDK only) |
| `blackdoor-common` | Ring buffers, token buckets, clocks (`TimeSource`), math, bounded queues, `Result` types. | api |
| `blackdoor-config` | Config model, schema validation, migrations, atomic reload, profiles. | common |
| `blackdoor-protocol` | `ClientVersion`/`ServerVersion` model, feature flags per version, Black Door packet interfaces, `PacketSource` SPI. | common |
| `blackdoor-protocol-packetevents` | PacketEvents-backed implementation of `PacketSource`. The only module importing PacketEvents. | protocol |
| `blackdoor-packets` | Packet security engine: state-machine, size, rate, order, field-bound validation. | protocol, config |
| `blackdoor-state` | Player state engine: positions, rotations, transactions, effects, attributes, inventory model, vehicle, latency. | protocol, config |
| `blackdoor-world` | Per-player client world view (collision palette), block-shape registry per version. | state |
| `blackdoor-physics` | Movement simulation per client version; uncertainty model. | state, world |
| `blackdoor-exploits` | Item/NBT/component validators, payload guards, book/sign limits, creative action guards. | packets, state |
| `blackdoor-detection` | Check runtime + check implementations. | physics, state, world, api |
| `blackdoor-behavior` | Statistical aggregation (off hot path). | state, api |
| `blackdoor-violations` | Violation ledger, decay, correlation, evidence capture. | api, config |
| `blackdoor-enforcement` | Policies, action selection, cooldowns, exemption resolution, `ActionRequest` emission. | violations, config |
| `blackdoor-network` | Connection, handshake, ping, login throttling; quarantine; proxy-forwarding validation logic. | common, config |
| `blackdoor-storage` | Async repositories, migrations, retention, backends. | api, config |
| `blackdoor-telemetry` | Metrics registry, structured security log, alert fan-out, webhooks, Prometheus text output. | api, config |
| `blackdoor-core` | Wires modules, owns lifecycle and command logic. | all domain modules |
| `blackdoor-platform-*` / `blackdoor-proxy-*` | Implement Platform SPI; translate platform events; schedule actions on correct threads. | core |
| `blackdoor-integrations` | Optional bridges (Via*, Geyser/Floodgate, anti-xray). | core, platform SPI |
| `dist-*` | Shade + relocate + generate plugin descriptors. | platform modules |

**Forbidden:** detection → enforcement (checks report, never act); any domain module → platform
module; any module except `protocol-packetevents` → PacketEvents types; any module → NMS.
Enforced by a Gradle dependency-rule check and an ArchUnit-style test (Phase 1).

## 3. Dependency Graph (acyclic)

```text
api ◄─ common ◄─ config
          ▲         ▲
       protocol ◄───┤
       ▲   ▲        │
packets   state ◄───┤
   ▲      ▲  ▲      │
exploits world │    │
          ▲    │    │
        physics│    │
          ▲    │    │
       detection behavior   violations ◄─ enforcement     network   storage   telemetry
          ▲         ▲            ▲             ▲             ▲         ▲         ▲
          └─────────┴────────────┴──── core ───┴─────────────┴─────────┴─────────┘
                                         ▲
             platform-bukkit · platform-paper · proxy-velocity · proxy-bungee · integrations
                                         ▲
                           dist-bukkit · dist-velocity · dist-bungee
```

Proxy distributions include only: api, common, config, protocol(+packetevents if used on proxy),
network, violations (subset), telemetry, storage, core-proxy wiring. They exclude physics,
world and detection.

## 4. Runtime Data Flow (backend)

```text
Netty event loop (player P)
  inbound bytes
   └► [decoder, Via translation if present]
       └► PacketSource (PacketEvents adapter) ─► BdPacket (immutable view, no copies of large payloads)
           1. NetworkGuard: per-connection packet-rate & byte budget  ─ fail → drop / disconnect
           2. PacketValidator: state machine, size, field bounds     ─ fail → drop + record
           3. ExploitGuards: item/NBT/payload/book/sign/creative      ─ fail → drop + record
           4. StateEngine.apply(packet)  (client-side view of P)
           5. Physics.predict() when movement packet                  ─ produces MovementVerdict
           6. CheckRunner.run(checks subscribed to packet type)       ─ produces Findings
           7. ViolationSink.offer(Finding)   (lock-free, bounded MPSC)
  outbound packets (server → P)
   └► StateEngine.observeOutbound(): velocity, teleports, chunks, effects, entity moves
       └► TransactionManager brackets security-relevant changes (ADR-008)

Async "security worker" (1–2 threads, bounded queue)
   ViolationLedger.update → Correlator → EnforcementPolicy.decide → ActionRequest
   Evidence → Storage queue (bounded; drop-oldest-evidence on saturation, never block)
   Telemetry counters / alert fan-out (rate limited)

Platform scheduler (main thread / Folia entity scheduler / proxy executor)
   ActionRequest → kick, setback teleport, cancel, quarantine, message staff
```

### 4.1 Why checks never act directly
Checks produce **Findings** (check id, confidence, uncertainty flags, evidence). The violation
and enforcement layers decide. This keeps checks testable in isolation, makes enforcement
policy auditable in one place, and prevents third-party checks from bypassing policy.

### 4.2 Immediate mitigations
Some responses must be synchronous to be useful: dropping an invalid packet, or cancelling an
impossible interaction. These are **mitigations**, not punishments: a check may return
`MITIGATE_DROP` or `MITIGATE_SETBACK` alongside its Finding; the packet layer honors drops
immediately, and setbacks are queued to the platform scheduler. Mitigations are configurable
per check and are logged.

## 5. Threading & Ownership Rules

| State | Owner thread | Readers | Mechanism |
|---|---|---|---|
| `PlayerSecurityState` (positions, transactions, physics) | Player's Netty event loop | Async workers via immutable snapshots | Single-writer; snapshot publishing via volatile reference |
| Client world view | Player's Netty event loop | Same | Single-writer |
| Violation ledger | Security worker | Commands, API via snapshots | Single-writer + copy-on-read |
| Connection tracker (network) | Netty threads (many) | Commands | Striped concurrent structure with hard size cap |
| Config | Reload thread | All | Immutable config objects swapped atomically |
| Bukkit/Folia entity actions | Main thread / entity's region thread | — | `PlatformScheduler.runForEntity(player, task)` |

Rules:
1. No `synchronized`/locks on the Netty hot path except bounded, documented cases.
2. Platform world/entity APIs only via `PlatformScheduler`; enforced by code review + tests that
   run with a thread-assertion wrapper.
3. All queues bounded; every queue has a declared overflow policy (drop-newest, drop-oldest,
   or shed-load) and an overflow counter.
4. Blocking calls (JDBC, HTTP, file I/O) only on dedicated I/O executors.

## 6. Player Security State (summary)

Per player, bounded:
- Position/rotation history: ring buffer (default 40 ticks).
- Velocity queue: pending server-applied velocities awaiting acknowledgement.
- Teleport queue: pending teleports (setback-aware).
- Transaction log: outstanding transaction IDs with send time (bounded; overflow ⇒ uncertainty
  flag + optional disconnect for clients that never acknowledge).
- Effects/attributes: packet-derived, transaction-anchored.
- Entity tracker: positions of entities the client sees, with interpolation state, for reach.
- Inventory model: server-authoritative copy of open container + cursor + hotbar slot.
- Vehicle state, abilities (flying allowed, speed), game mode.
- Latency stats: RTT EWMA, jitter, ack backlog.
- Environment: world/dimension id, world border, server tick health (published from server).

Details: [PHYSICS_ENGINE.md](PHYSICS_ENGINE.md).

## 7. Failure Handling Summary

| Subsystem | Failure | Behavior | Fail mode |
|---|---|---|---|
| Single check | Exception / time budget exceeded | Check disabled for that player for a cooldown; circuit breaker opens after N failures globally; alert | **Open** (no enforcement from broken check) |
| Physics | State desync (unknown block, missing chunk) | Mark uncertainty; skip movement checks until resync event | **Open** |
| Packet validator | Internal error | Pass packet through, record error | **Open** (must not break gameplay due to own bug) |
| Exploit guard (crash-class) | Internal error | Drop the packet that triggered the error, record | **Closed** for that packet only |
| Network throttle | Tracker full | Evict LRU; switch to stricter global limits | **Degraded-closed** for new connections under attack |
| Storage | DB down / queue full | Keep in-memory ledger; shed evidence first, then low-priority events; retry with backoff | **Open** (gameplay unaffected) |
| Telemetry / webhooks | Endpoint down | Drop with counter; never retry unbounded | **Open** |
| Config reload | Invalid file | Keep previous config; report errors | **Previous-good** |
| Proxy link | Disconnected | Backend continues independently | **Open** |
| Forwarding validation | Unverifiable identity | Refuse login | **Closed** |
| Unsupported version | Unknown protocol | Protocol-level guards only; physics/check modules disabled for that client; staff notice | **Open** for checks, guards still apply |

Per-subsystem rationale is repeated in each subsystem document.

## 8. Extension Points

- `Check` SPI (custom checks), `ExemptionProvider`, `EnforcementAction`, event bus for
  violations/enforcement/network events. See [DEVELOPER_API.md](DEVELOPER_API.md).
- Third-party code receives read-only snapshots and cannot suppress core packet/exploit guards
  (they are not exposed as exemptible through the API).
