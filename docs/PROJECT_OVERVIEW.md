# Black Door — Project Overview

> **Phase:** 1 (Foundation) — Phase 0 approved 2026-11-26
> **Implementation status:** Foundation only — build system, platform adapters, configuration
> framework, version model and admin commands. **No security protection is implemented yet.**
> Every protection named in these documents is **planned**. Live status: [../IMPLEMENTATION.md](../IMPLEMENTATION.md).

## 1. What Black Door Is

Black Door (internal namespace `blackdoor`) is a planned server-side security framework for
Minecraft Java Edition networks. It combines:

| Pillar | Summary |
|---|---|
| Packet security | Centralized validation of inbound packets (state, size, rate, order, field bounds). |
| Player state | Authoritative, packet-derived model of what each client knows and does. |
| Physics prediction | Version-aware simulation of legitimate movement, used to bound what a client may do. |
| Detection | Independent, configurable checks (combat, movement, world, inventory, packet, automation). |
| Exploit prevention | Version-specific guards against crash, dupe and resource-exhaustion exploits. |
| Network protection | Application-layer connection, login and ping throttling; proxy trust validation. |
| Behavior analysis | Explainable, statistical signals that feed staff review — never sole punishment basis. |
| Violations & enforcement | Central violation ledger, correlation, evidence, graded responses. |
| Storage, telemetry, admin | Persistence, metrics, alerts, commands, developer API. |

## 2. What Black Door Is Not

- Not a guarantee of complete cheat detection. Many cheats produce no server-observable evidence.
- Not a volumetric DDoS solution. A plugin runs after the kernel has accepted traffic; bandwidth
  floods must be absorbed upstream (see [DDOS_LIMITATIONS.md](DDOS_LIMITATIONS.md)).
- Not a client-side anti-cheat. It never ships code to players.
- Not an automated permanent-ban system by default. Defaults are monitoring-only.

## 3. Guiding Principles

1. **Correctness before coverage.** A check that false-flags is worse than no check.
2. **Server-authoritative, uncertainty-aware.** Every verdict carries a confidence and the
   uncertainty conditions under which it was produced (lag, teleport, translation, Bedrock).
3. **Never block the server.** No blocking I/O on Netty event loops or server/region threads.
4. **Bounded everything.** Every queue, buffer, cache and per-player structure has a hard cap.
5. **Explainable enforcement.** Every automated action records the evidence that caused it.
6. **Secure defaults, least privilege.** Nothing listens publicly, nothing modifies host firewalls,
   nothing phones home unless an administrator explicitly enables it.
7. **Honest documentation.** Every capability is labeled Implemented / Partial / Experimental /
   Planned / Unsupported. Compatibility is claimed only after integration tests pass.

## 4. Phase 0 Deliverables

| # | Deliverable | Document |
|---|---|---|
| 1 | Technical feasibility assessment | [ARCHITECTURAL_REVIEW.md §2](ARCHITECTURAL_REVIEW.md) |
| 2 | Architectural proposal | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 3 | Version compatibility strategy | [PROTOCOL_ARCHITECTURE.md](PROTOCOL_ARCHITECTURE.md) |
| 4 | Platform compatibility matrix | [COMPATIBILITY_MATRIX.md](COMPATIBILITY_MATRIX.md), [compatibility-matrix.yml](compatibility-matrix.yml) |
| 5 | Dependency evaluation | [ARCHITECTURAL_REVIEW.md §5](ARCHITECTURAL_REVIEW.md) |
| 6 | Detection registry | [DETECTION_REGISTRY.md](DETECTION_REGISTRY.md) |
| 7 | Threat model | [THREAT_MODEL.md](THREAT_MODEL.md), [SECURITY_REQUIREMENTS.md](SECURITY_REQUIREMENTS.md) |
| 8 | Network security architecture | [NETWORK_SECURITY.md](NETWORK_SECURITY.md), [DDOS_LIMITATIONS.md](DDOS_LIMITATIONS.md) |
| 9 | Performance engineering strategy | [PERFORMANCE_BUDGET.md](PERFORMANCE_BUDGET.md) |
| 10 | False-positive prevention strategy | [FALSE_POSITIVE_POLICY.md](FALSE_POSITIVE_POLICY.md) |
| 11 | Testing methodology | [TESTING_STRATEGY.md](TESTING_STRATEGY.md) |
| 12 | Phased roadmap | [ROADMAP.md](ROADMAP.md) |
| 13 | Known limitations & open decisions | [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md), [ARCHITECTURAL_REVIEW.md §7](ARCHITECTURAL_REVIEW.md) |

Supporting design documents: [PHYSICS_ENGINE.md](PHYSICS_ENGINE.md),
[EXPLOIT_PREVENTION.md](EXPLOIT_PREVENTION.md), [CONFIGURATION.md](CONFIGURATION.md),
[DEVELOPER_API.md](DEVELOPER_API.md).

Placeholders that will be filled in later phases (they currently state that no work exists):
[BENCHMARK_REPORT.md](BENCHMARK_REPORT.md), [DEPLOYMENT.md](DEPLOYMENT.md),
[SECURITY_AUDIT.md](SECURITY_AUDIT.md).

## 5. Feature Status Vocabulary

| Label | Meaning |
|---|---|
| **Implemented** | Code merged, tests pass, integration-tested on every platform listed as supported. |
| **Partial** | Implemented for a subset of versions/platforms; gaps documented. |
| **Experimental** | Implemented, disabled by default, not validated for enforcement. |
| **Planned** | Designed, not implemented. |
| **Unsupported** | Explicitly out of scope, with the reason documented. |

As of Phase 0, every feature is **Planned**.
