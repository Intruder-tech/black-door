# Testing Strategy

> **Phase:** 0 · **Status:** Methodology draft for review.

## 1. Test Pyramid

| Level | Scope | Tools | Runs |
|---|---|---|---|
| Unit | Pure logic: validators, token buckets, physics steps, ledger, config schema | JUnit 5, AssertJ | Every commit |
| Property-based | Invariants: validators never throw on arbitrary input; buckets never exceed capacity; physics determinism; config round-trip | jqwik | Every commit |
| Fuzz | Packet adapter, NBT/component validators, YAML loader, webhook URL validator | Jazzer | Nightly + time-boxed on PRs |
| Golden physics | Recorded legitimate movement traces per version replayed through the engine; must produce 0 flags | Custom replay harness (`blackdoor-testing`) | Every commit |
| Cheat replay | Recorded/synthesized cheat traces (from test clients we control) must flag | Replay harness | Every commit |
| Integration | Real server jars (matrix of platform × version) with headless bots: join, move, fight, inventory, teleports, Via clients | Gradle task launching servers in containers; MCProtocolLib-based bots | Nightly + release |
| Proxy integration | Velocity/BungeeCord + backends; forwarding validation; spoof attempts must fail | Same | Nightly |
| Concurrency | Single-writer invariants, scheduler thread assertions, Folia region ownership | Thread-assertion wrapper, jcstress for core primitives | Every commit (jcstress nightly) |
| Memory/leak | Join/leave cycles; weak-reference assertions; heap after N cycles stable | JFR, custom harness | Nightly |
| Performance | JMH micro + scenario benchmarks | JMH, bots | Nightly; release report |
| Security verification | SR-xx requirements (SECURITY_REQUIREMENTS.md) each mapped to a test or static rule | JUnit, Semgrep/ErrorProne rules, ArchUnit-style dependency tests | Every commit |
| Regression | Every fixed bug/FP gets a test | All | Every commit |

## 2. Version Matrix

Representative versions (OD-5): 1.8.8, 1.12.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.4,
1.20.6, 1.21.1, 1.21.4, latest 1.21.x, latest 26.x × Spigot/Paper (+ Folia, Purpur for ≥ 1.20)
× Via client versions (oldest supported, server version, newest).

## 3. Trace Recording

- Legit traces recorded from **consenting** testers on our own test servers (packet-level logs,
  no chat content, pseudonymous IDs).
- Cheat traces produced only on isolated test servers by the project team.
- Traces stored in a versioned corpus with metadata (client version, server version, scenario).

## 4. Golden Physics Tests

For each movement model: traces covering every mechanic in PHYSICS_ENGINE.md §4. Acceptance:
zero flags at default thresholds, and per-tick offset distribution recorded to detect drift.

## 5. Negative/Adversarial Tests

Malformed packets per state; out-of-order sequences; NaN/Inf; extreme coordinates; oversized
payloads; transaction forgery; forwarded-identity spoofing on backend; webhook SSRF targets; YAML
alias bombs; command permission bypass attempts.

## 6. Accuracy Evaluation (for later reporting)

- FP rate = flags on legit corpus / legit player-hours (per check, per version).
- Detection rate = flagged cheat traces / cheat traces (per check, per cheat feature).
- Reported only with corpus size, composition and date. No figures until the corpus exists.

## 7. Environment Rules

All load and attack tests run in isolated, project-owned environments. Never against public or
third-party servers.

## 8. CI Pipeline (planned)

`lint (spotless, checkstyle-lite) → compile (JDK 17 target, build on JDK 21/25) → unit/property →
dependency rules → static security rules → golden/cheat replay → package (shadow, relocation
check) → SBOM + vuln scan → [nightly] integration matrix, fuzz, jcstress, leak, benchmarks`.
