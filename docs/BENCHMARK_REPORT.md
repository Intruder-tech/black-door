# Benchmark Report

> Only measured results appear here. Each result lists its environment, method and limitations.
> Methodology: [PERFORMANCE_BUDGET.md §3](PERFORMANCE_BUDGET.md).

| Date | Scenario | Result | Section |
|---|---|---|---|
| 2026-11-26 | World-view memory spike (milestone 2.3, OD-3) | Per-player block copy rejected (10 MiB/player); shared position-keyed collision cache with a bounded simulation radius chosen | §1 |

---

## 1. World-view memory spike (milestone 2.3, decision OD-3)

**Question:** physics checks need to know which blocks the client can collide with. How much
memory does a per-player "world view" cost at 100 and 500 players, and which layout should
Black Door use?

### Environment

| Item | Value |
|---|---|
| Hardware | Intel Core i7-13700HX (16 cores / 24 threads), 15.7 GiB RAM, Windows 11 |
| JVM | Temurin 25.0.5 (Gradle toolchain), `-Xms2g -Xmx2g -XX:+UseG1GC` |
| Data | **Real terrain**: 609 fully generated overworld chunks (14,616 sections) from the Paper 26.3 test server world (spawn area plus areas the owner explored) |
| Harness | `./gradlew :blackdoor-benchmarks:worldViewSpike -Pregions=<region folder>` (module `blackdoor-benchmarks`, never shipped) |

### Layouts compared

| Layout | Description |
|---|---|
| **A** | Per player: copy of each section's block-state palette and packed data, as the client received it |
| **B** | Per player: collision-shape palette plus packed indices (1–2 bits per block in practice). Sections with a single shape are shared singletons |
| **C** | Shared pool of B-sections used by all players, plus a per-player array of references |

### Results (overworld)

Section statistics:

| Metric | Value |
|---|---|
| Average block-state palette size | 5.28 |
| Sections with a single block state | 57.0% |
| Sections with a single collision shape | 67.9% |
| Average shapes in non-uniform sections | 2.53 |
| Distinct block states / collision shapes seen | 442 / 147 |
| Identical non-uniform sections at different positions | 49 of 4,695 (1%) |

Memory per player at view distance 10 (441 chunks). "Measured" means the structures were
actually built on the heap and measured after GC:

| Layout | Per player (measured) | 100 players | 500 players |
|---|---|---|---|
| A: block-state copy | **9.99 MiB** (10 players built) | 1.0 GiB | 4.9 GiB (estimate) |
| B: collision shapes | **2.45 MiB** (100 players built) | 245 MiB | 1.2 GiB (estimate) |
| C: shared pool, players together | **0.08 MiB** (100 players built) | 8.4 MiB | ~23 MiB (estimate) |
| C: shared pool, players spread out | ≈ B (no overlap) | ≈ 249 MiB | ≈ 1.2 GiB |

Build cost of layout B (time to convert one received section, after JIT warm-up):

| Metric | Value |
|---|---|
| Median per section | 9.1 µs |
| Per chunk (24 sections) | 218 µs |
| Whole view on join (441 chunks) | ≈ 96 ms in total, spread over the chunk sends |

### Findings

1. **A is rejected.** At 10 MiB per player it costs about 5 GiB at 500 players.
2. **B is 4× smaller than A but still too large spread out** (1.2 GiB at 500 players). Two thirds
   of sections are a single shape; the rest need only 1–2 bits per block.
3. **De-duplicating by content barely helps.** Only 1% of non-uniform sections repeat elsewhere.
   Sharing works because **players look at the same chunks**, not because terrain repeats.
   Sharing must therefore be keyed by **world position**, not by content.
4. **Physics only needs blocks near the player.** Collisions are resolved within a few blocks of
   the player. Keeping a bounded **simulation radius** instead of the whole view distance cuts
   memory proportionally. Derived from the measured 5.7 KiB per chunk: radius 3 (7×7 = 49 chunks)
   costs ≈ 0.27 MiB per player with no sharing, so ≈ 136 MiB in the worst case (500 players all
   far apart) and much less when players share areas.
5. **Build cost is acceptable but not free** (~0.2 ms per chunk). With position sharing, each
   chunk is converted once server-wide rather than once per player.

### Decision (OD-3 resolved, ADR-017)

Use a **shared, position-keyed collision cache** (layout B encoding, keyed by dimension + chunk +
section), with:

- a **per-player overlay** of block changes the client has not yet acknowledged (transaction-anchored);
- a **simulation radius** (default 3 chunks; configurable) outside which movement checks give no verdict;
- **reference counting and eviction** of chunks no tracked player is near;
- a **memory budget** with a hard cap: when full, checks for affected players return "no verdict"
  instead of allocating more.

### Limitations

- One world, one machine, one version (26.3), overworld only. The nether sample (1 chunk) was too
  small and is not reported.
- The collision classifier in the spike is approximate (by block name). It affects shape counts but
  not the order of magnitude. The real shape registry comes from game data in milestone 2.4.
- 500-player figures are linear extrapolations of measured per-player costs. Heap overhead of the
  final implementation (hash maps, reference counts) is not included and must be re-measured once
  it exists.
- Explored terrain is around spawn; caves and builds in long-running worlds may raise the share of
  non-uniform sections.

---

## 2. Connection guard overhead (Phase 4)

Handshake plus login for unknown addresses during a flood, 10,000 addresses, 100,000 connections,
third round after warm-up. `./gradlew :blackdoor-benchmarks:networkOverhead`, 2026-11-26, same machine
as section 1. A plain timing loop, not JMH, so treat the figures as approximate.

| Players online | Before indexing by address | After |
|---|---|---|
| 0 | 0.72 us | 0.70 us |
| 100 | 1.13 us | 0.49 us |
| 500 | 4.72 us | 0.56 us |
| 2,000 | 10.91 us | 0.40 us |

Budget: 5 us per new connection (PERFORMANCE_BUDGET.md). Not measured: the cost of the platform
events around it, or memory under a large flood.
