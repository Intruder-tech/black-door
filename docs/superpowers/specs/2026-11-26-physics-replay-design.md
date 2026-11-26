# Trace replay and vertical movement: design

**Date:** 2026-11-26 · **Status:** approved by the owner · **Size:** project step
· **Step 2 of milestone 2.4b** (method: [PHYSICS_ENGINE.md](../../PHYSICS_ENGINE.md))

## Intent
Prove, on the owner's recorded play, that predicted height matches what the client did, before any
of it can touch a player.

**Not goals:** horizontal movement, fluids, climbing, special blocks, live server wiring, findings,
enforcement, versions other than 26.3.
**Failure would be:** a miss on honest play, or a pass obtained by giving no verdict.

## Approaches
One sensible way, already settled by PHYSICS_ENGINE.md: simulate each tick and compare with the
reported position. Fixed speed limits were rejected there, as they either miss cheats or flag
honest players. Vertical movement comes first because the traces already confirm its rule.

## Evidence (throwaway script, 2026-11-26)
- `dy' = (dy - 0.08) * 0.98` explains 99.4 % of consecutive airborne ticks of trace 102 within 1e-5.
- A jump starts at dy 0.42; with jump boost II at 0.62 (0.42 + 0.1 per level).
- A client tick ends with a `tick` event and carries at most one `move`; none when nothing changed.
- The misses in traces 101 and 104 include their creative-mode parts, which the script did not skip.

## Design
- New module `blackdoor-physics` (no platform or packet-library code). Depends on
  `blackdoor-protocol` for `BlockShapes`.
- It reads a format-2 trace into ticks, keeps the world from `chunk`, `block` and `unload`, and
  replays each tick: predict dy from the previous accepted state, clip it against the collision
  boxes under and over the player's box (0.6 wide, 1.8 tall, 1.5 sneaking), compare with the
  reported height.
- The accepted state is the reported position, so errors do not add up.
- Each tick yields **match**, **miss** (with the offset) or **no verdict** (with a reason).
- **No verdict:** creative or flying, fluid, climbable or special block touching the player,
  unknown shape or chunk, unmodelled effects (levitation, slow falling), pending velocity,
  teleport not yet confirmed and the first tick after it, elytra.
- Jumping is allowed when the player was on the ground and the jump key is held.

## Pass mark
For traces 101, 102 and 104: zero misses, and at least 80 % of airborne survival ticks in trace
102 get a verdict. The test prints the no-verdict reasons with counts.
Tolerance is one named constant, 1e-4, and is only changed with the reason written down.

## Known limits
26.3 only. Step-up while walking is seen as vertical movement and is part of this step only as far
as the traces exercise it; where it cannot be predicted from height alone the tick is no verdict.

## Left to the builder
Class names, trace parsing without new third-party dependencies, internal layout.
