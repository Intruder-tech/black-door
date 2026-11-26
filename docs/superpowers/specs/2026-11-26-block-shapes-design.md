# Block collision shapes for physics: design

**Date:** 2026-11-26 · **Status:** awaiting owner approval · **Size:** small change
· **Step 1 of milestone 2.4b** (see [PHYSICS_ENGINE.md](../../PHYSICS_ENGINE.md))

## Intent
Physics needs the exact collision boxes of every block state. Today `ShapeRegistry` only gives a
coarse class, and PacketEvents carries no shapes.

**Not goals:** movement prediction, checks, anything that flags or blocks a player.
**Failure would be:** a wrong shape that later flags an honest player.

## Approaches
| Approach | Trade-off |
|---|---|
| **A. Ask the running server** (chosen by the owner) | Exact for the server's version. Older ViaVersion clients may see different shapes. |
| B. Ship tables per version | Consistent, but a new table for every version and a bigger jar |
| C. Hand-written rules | No server dependency, error-prone |

## Design
- **No server internals needed** (better than first described): PacketEvents turns a state id into
  its name and properties, `Server.createBlockData(String)` turns that into a `BlockData`, and
  `BlockData.getCollisionShape(Location)` gives the boxes. The last is checked present in the Paper
  26.3 API.
- `blackdoor-protocol`: a lookup from state id to a list of boxes, or **unknown**.
- `blackdoor-platform-bukkit`: builds the table on the server thread, a limited number of states
  per tick, after startup. Packet threads only read the finished entries.
- **Unknown** means no verdict. It applies while the table is still building, when the server has
  no such API, on a failed state, and to blocks whose shape depends on where they are or on who
  stands on them (scaffolding, powder snow, bamboo, pointed dripstone and the like).
- `/blackdoor diagnostics` shows how many states are ready.
- A console-only dump writes the table to a gzip file. I run it once on a temporary 26.3 server
  and commit the result as test data, so traces can be replayed without a server.

## Known limits
- Servers whose API lacks `getCollisionShape` get no physics.
- Clients older than the server may see other shapes; physics for them stays off until tested.
- Friction and speed factors (ice, soul sand, honey) are not in this step.

## Verification
Unit tests for the lookup and file format. A temporary-server run checking known shapes (full
block, bottom slab, stairs, fence 1.5 high, air) and that every block under the recorded traces
has a known shape or a listed reason. The server thread's time per tick is measured.

## Left to the builder
Class names, states per tick, file layout.
