# Roadmap

> **Phase gating:** a phase starts only after the previous phase's acceptance criteria are met
> and the owner approves. Progress is tracked in [../IMPLEMENTATION.md](../IMPLEMENTATION.md).

## Phase 0 — Research & Architecture *(done — approved 2026-11-26)*
**Deliverables:** documents listed in PROJECT_OVERVIEW.md §4.
**Acceptance:** owner reviews and approves; open decisions OD-1, OD-2, OD-4, OD-5, OD-6 resolved.

## Phase 1 — Foundation *(current — code complete, acceptance partially verified; see IMPLEMENTATION.md)*
- Gradle multi-module skeleton with dependency rules; toolchains (build JDK 21/25, target 17).
- `api`, `common`, `config` (schema, migration, atomic reload), `protocol` (version/feature model),
  `protocol-packetevents` adapter, `PlatformScheduler` + platform adapters (bukkit, paper/folia,
  velocity, bungee) that load, log, and shut down cleanly.
- Commands skeleton: `help`, `status`, `version`, `reload`, `diagnostics` (real, not stubs).
- CI pipeline: build, unit, dependency rules, static security rules, SBOM, vuln scan.
**Acceptance:** plugin loads and unloads without errors or leaks on every CI matrix platform;
config reload rollback tested; dependency graph check passes; no feature is advertised.

## Phase 2 — Packet Security, State, Physics Core

> **Closed by the owner on 2026-11-26.** The physics core was not finished: only height is predicted,
> only in tests, for 26.3. Black Door has no movement detection, and the Phase 3 movement checks that
> depend on prediction are dropped unless the physics work is reopened. The physics acceptance
> criteria, validator fuzzing and the per-packet latency benchmark below were not met.

- Packet security engine stages 1–6; `packet.*` deterministic checks (monitor + drop invalid).
- Player state engine; transaction manager; entity tracker.
- World view spike (OD-3) with measurements, then implementation.
- Physics core (walking/jumping/collisions/effects/knockback) for 2–3 representative models first
  (proposal: 1.8.8, 1.21.x latest, 26.x latest), then remaining models.
- Golden-trace harness.
**Acceptance:** zero flags on golden legit traces for covered versions; validator fuzzing clean;
per-packet latency within budget in JMH.

## Phase 3 — Detections
Movement, combat, world, inventory checks per DETECTION_REGISTRY.md (Deterministic/High first).
**Acceptance:** each check meets its registry contract; FP corpus pass; cheat replay detection
demonstrated; all checks default to monitor.

## Phase 4 — Exploits, Network, Proxies
Exploit guards; `blackdoor-network`; Velocity/Bungee adapters; forwarding validation;
anti-xray integration.
**Acceptance:** adversarial tests pass; proxy spoof tests fail as expected; network module
overhead measured.

## Phase 5 — Behavior, Correlation, Enforcement
Statistical checks (review-only), correlator, enforcement policies, setbacks, quarantine,
evidence capture, staff review flow.
**Acceptance:** policy engine refuses to punish on statistical-only evidence (tested); audit log
complete.

## Phase 6 — Storage, Admin, Telemetry, API
SQLite/PostgreSQL/MariaDB (+ Redis decision OD-7), migrations, retention; full command set;
alerts, Prometheus text endpoint, webhooks (SSRF-guarded); public API v1.
**Acceptance:** DB outage tests; API compatibility tests; SR-xx tests for these surfaces.

## Phase 7 — Hardening & Verification
Full version matrix, security audit (SECURITY_AUDIT.md), performance benchmarks at 50–1000
players (BENCHMARK_REPORT.md), optimization, leak testing.
**Acceptance:** budgets met or deviations justified; audit findings resolved or accepted.

## Phase 8 — Release
Signed artifacts, SBOM, DEPLOYMENT.md, configuration profiles, compatibility matrix updated from
CI only.

## Later / Optional
`blackdoor-edge` agent; entity-hiding anti-ESP (experimental); vehicle physics; ML research
(only with labeled dataset and measured benefit).
