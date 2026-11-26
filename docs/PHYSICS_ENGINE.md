# Physics Prediction Engine

> **Phase:** 0 · **Status:** Proposed design · **Implementation phase:** 2 (core), 3 (coverage)

## 1. Goal

Given the client's last accepted state and everything the server has told the client, compute
the **set of physically valid next positions** and test whether the client's reported movement
falls inside it, within an explicit uncertainty margin. This replaces fixed speed thresholds.

## 2. Model

```text
             inputs I ∈ {forward/back/none} × {left/right/none} × {jump?} × {sprint?} × {sneak?}
                         (≤ 1.21.1: enumerated; ≥ 1.21.2: reported by input packet [verify])
 state S_t ──► for each candidate I and each pending server effect E (velocity, teleport, block change):
                  S'_{t+1} = Simulate(S_t, I, E, world view, version model)
           ──► best = argmin_I,E distance(S'_{t+1}.pos, reported.pos)
           ──► offset = distance(best, reported)
           ──► verdict: offset ≤ ε(uncertainty) ? ACCEPT : FLAG(offset, confidence)
```

- **Candidate pruning:** order candidates by likelihood (previous input first), stop at the first
  candidate within ε. Typical cost ≈ 1–3 simulations per tick; worst case bounded by a fixed
  candidate cap.
- **State advance:** accepted state is the client's reported position (to avoid drift), with
  velocity taken from the best simulation.

## 3. Version-Specific Movement Models

`MovementModel` implementations, selected by `ClientVersion`:

| Model | Client versions | Notable differences |
|---|---|---|
| `Legacy8` | 1.8.x | Idle flying packet; no swimming/elytra; legacy collision; sneak edge behavior. |
| `Combat9to12` | 1.9–1.12.2 | Elytra; levitation; small-movement suppression; offhand use slowdown. |
| `Aquatic13to16` | 1.13–1.16.5 | Swimming, bubble columns, flattened block shapes; soul speed; honey (1.15). |
| `Modern17to20` | 1.17–1.20.x | Powder snow; world height changes; ping/pong transactions. |
| `Modern21` | 1.21.x | Input packet (1.21.2+) [verify]; mace/wind charge knockback; attribute-driven movement (step height, gravity, jump strength attributes) [verify]. |
| `Year26` | 26.x | Onboarded per release (see PROTOCOL_ARCHITECTURE.md §6). |

Each model is a set of composable, unit-tested steps:
`applyInput → applyFriction(block, effects, attributes) → applyGravity → handleFluids →
handleClimbable → handleWeb/Powder/Honey/Slime → collide(world view) → stepUp → postCollision`.

Constants and formulas are written from the behavior specification for that version and covered
by golden tests from recorded legitimate clients (TESTING_STRATEGY.md §4). No code is copied from
other projects (clean-room policy).

## 4. Mechanics Coverage Plan

| Mechanic | Phase | Notes |
|---|---|---|
| Walking, sprinting, sneaking, jumping, gravity, ground friction, air drag | 2 | Core. |
| Block collisions (full and partial shapes), step-up | 2 | Requires block-shape registry per version. |
| Ice / packed / blue ice, soul sand, slime, honey, cobweb, sweet berry, powder snow | 2–3 | Friction & speed multipliers. |
| Water, lava, swimming, bubble columns, depth strider, dolphin's grace | 3 | Fluid physics are version-sensitive; high FP risk → monitor first. |
| Ladders, vines, scaffolding (climbing) | 3 | |
| Potion effects: speed, slowness, jump boost, levitation, slow falling | 2 | Transaction-anchored. |
| Attributes (movement speed, step height, gravity, jump strength, scale) | 3 | Modern versions expose these as attributes [verify]. |
| Elytra gliding, fireworks boosts, riptide | 3 | Fireworks as entity boost; riptide only in water/rain. |
| Knockback, explosions, server-set velocity | 2 | Pending velocity queue; accept with or without application until acked. |
| Vehicles (boat, minecart, horse, pig, strider, camel) | Later (post-3) | Vehicle physics are complex; initial support = bounded speed/teleport sanity only. |
| Entity collisions (pushing) | 3 | Treated as uncertainty source. |
| Teleports, respawn, world change | 2 | Hard resync points. |

### Verified on 26.3 against recorded play (2026-11-26)

- Gravity: next vertical speed is `(speed - 0.08) * 0.98`; speeds under 0.003 become 0.
- Jump: 0.42, plus 0.1 per jump boost level.
- Vertical movement is resolved at the old horizontal position; a landing or grounded player then
  steps up to 0.6 onto a block at the new position.
- Being on the ground is worked out from the blocks; a disagreeing client claim is a miss.
- Knockback shows up as exactly the sent vertical velocity, followed by normal gravity.
- The tick after moving through water still carries water drag, so it gets no verdict.

Only height is predicted so far, only in tests (`blackdoor-physics`). Horizontal movement,
fluids, climbing and special blocks are not modelled.

## 5. Uncertainty Model

ε (accepted offset) = base precision + Σ uncertainty terms, each **bounded and logged**:

| Source | Term |
|---|---|
| Floating-point / client rounding | constant (~1e-4 order; tuned by golden tests) |
| Pending unacknowledged velocity | either-or evaluation (not additive slack) |
| Pending block changes near the player | evaluate both old and new block states |
| Entity pushing | bounded per-tick push magnitude |
| Small-movement suppression (missing updates) | accumulate allowed movement across skipped ticks |
| Unknown/unloaded chunk in view | **skip** (no verdict) |
| Server lag (MSPT > threshold) | widen timing tolerances; never widen spatial ones beyond cap |
| Recent teleport/respawn/world change | grace window until teleport confirmed |
| Plugin-applied velocity via API | observed as outbound velocity packet ⇒ handled like knockback |
| Protocol translation | per-version feature flags; extra tolerance only where documented |

If uncertainty exceeds a cap, the engine returns `NO_VERDICT` rather than a widened accept/flag.
Checks treat `NO_VERDICT` as "no evidence", never as a violation.

## 6. Setbacks

When enforcement or a check's mitigation requests a setback, the server teleports the player to
the last accepted position (validated non-colliding), via `PlatformScheduler` on the owning
thread. Setbacks are rate-limited to avoid rubber-band loops; repeated setbacks with no progress
escalate to staff alert rather than infinite teleporting.

## 7. World View (`blackdoor-world`)

- Built from outbound chunk data and block updates (what the client actually received).
- Stores collision-relevant state only (block-state → shape id palette).
- Bounded per player by a radius budget; outside the radius ⇒ `NO_VERDICT` for movement there.
- **Decided (ADR-017, measured in BENCHMARK_REPORT.md §1):** shared position-keyed collision
  cache + per-player overlay of unacknowledged changes + simulation radius (default 3 chunks) +
  memory budget. The options considered were:
  1. Per-player compact palettes (simplest, highest memory).
  2. Shared immutable section snapshots + per-player overlay of unacknowledged changes.
  3. Hybrid: shared for static regions, per-player near the player.
- Spike acceptance: measured heap per player at view distance 10 with 100 and 500 simulated
  players, and CPU cost of chunk ingestion, recorded in BENCHMARK_REPORT.md.

## 8. Known Physics Limitations

- Modded or plugin-altered physics (Purpur options, custom movement plugins) require exemptions
  or per-server configuration; Black Door cannot infer them.
- Bedrock (Geyser) movement is not simulated.
- Vehicles have weak coverage initially.
- New Minecraft drops can change physics; until onboarded, prediction is disabled for that version.
