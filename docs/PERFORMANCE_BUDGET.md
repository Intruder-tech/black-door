# Performance Budget & Engineering Strategy

> **Phase:** 0 · **Status:** Targets only. **No measurements exist yet.** Targets are design goals,
> to be validated (or revised with justification) by benchmarks in BENCHMARK_REPORT.md.

## 1. Budgets (targets)

| Metric | Target | Notes |
|---|---|---|
| Main/region thread cost | ≈ 0 per packet; ≤ 0.5 ms per tick aggregate at 500 players | Only scheduled actions + tick-health sampling touch the main thread |
| Netty per-packet processing (movement, p99) | ≤ 50 µs | Includes validation, state update, 1–3 simulations, checks |
| Netty per-packet processing (non-movement, p99) | ≤ 10 µs | |
| Hot-path allocation | ~0 bytes per movement packet in steady state | Object pools / mutable scratch per player; verified via JMH `-prof gc` |
| Heap per player (excluding world view) | ≤ 256 KB | Bounded buffers |
| Heap per player (world view) | Decided by OD-3 spike; candidate target ≤ 2 MB at view distance 10 | Primary risk |
| Security worker queue latency (p99) | ≤ 50 ms | Violation → enforcement decision |
| Storage | Never on game threads; queue bounded (default 10k events) | |
| Network module per new connection | ≤ 5 µs | Token bucket + map lookup |
| Startup | ≤ 2 s added | Registry and data loading |

## 2. Techniques

- **Single-writer per player** on Netty thread — no locks on hot path.
- **Primitive structures**: ring buffers of `double[]`; avoid boxing; consider minimal fastutil.
- **Allocation discipline**: reusable scratch objects per player; no streams/lambdas on hot path;
  lazy decoding of large payloads.
- **Cheap-first ordering**: size/state/rate checks before field decoding before simulation.
- **Candidate pruning** in physics (most-likely input first; early exit).
- **Async, batched persistence & telemetry**; metrics as `LongAdder`s sampled periodically.
- **Rate-limited logging** with per-category token buckets.
- **Cleanup**: player state released on disconnect via channel close hook; leak detector in tests
  (weak-reference assertions after disconnect).
- **Platform-aware scheduling**: `PlatformScheduler` with Bukkit, Paper/Folia (entity/region/global)
  and proxy implementations.
- **Circuit breakers**: per-check time budget; repeated overruns disable the check and alert.

## 3. Benchmark Plan

| Scenario | Players | Method |
|---|---|---|
| Idle + walking | 50, 100, 250, 500, 1000* | Headless protocol bots on isolated hardware; scripted legit movement |
| Combat arena | 50, 100, 250 | Bots fighting with knockback |
| Join storm | 500 joins in 10 s | Network module + state init |
| Attack simulation | Connection/ping/login floods from local load generator | Isolated lab network only |

\*1000 only where infrastructure permits.

Measured: CPU %, heap & GC (JFR), packet processing latency histogram (HdrHistogram internal),
detection latency, TPS & MSPT delta vs. baseline without Black Door, FP count, network-module
overhead. Recorded with hardware, OS, JVM, flags, server versions, methodology, limitations.

**Rules:** benchmarks run only against servers we own in isolated environments; results are
reported as measured, including unfavorable ones; no extrapolated numbers presented as measured.

## 4. CI Performance Gates

JMH microbenchmarks for: validator pipeline, physics step, reach computation, token bucket,
violation ledger update. CI fails on > 15% regression vs. stored baseline on the same runner class
(noise budget to be calibrated in Phase 1).
