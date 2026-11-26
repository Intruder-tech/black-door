# Golden traces

Recordings of **legitimate** play made with `/blackdoor record` on a local test server
(milestone 2.4b). The physics engine must replay every trace here with **zero findings**.
Player names are replaced with `tester`.

| File | Client / server | Content | Events |
|---|---|---|---|
| `26.3/trace-001-survival-mixed.jsonl` | 26.3 / 26.3 | 60 s survival play by the owner (2026-11-26) | 1,066 movement, 550 airborne, 6 knockback, 7,373 block changes |

Format: JSON lines. The first line is `meta`; then `move` (position/rotation/ground as sent by the
client), `velocity` (server knockback for the player), `teleport`, `world` and `block` events,
each with `t` = nanoseconds since the start of the recording.
| `26.3/trace-002-swim-climb-ice.jsonl` | 26.3 / 26.3 | 59 s: swimming, ladders, ice (y 56 to 104) | 1,163 movement, 919 airborne/in water, 4 knockback, 2,183 block changes |
| `26.3/trace-003-knockback-fall.jsonl` | 26.3 / 26.3 | 19 s (ended early): mob knockback, small falls (max ~2.6 blocks) | 255 movement, 101 airborne, 2 knockback, 190 block changes |

## Format 2 (2026-11-26)

The three traces above are format 1 and cannot test physics: they have no terrain, keys, effects,
attributes or teleport destination. They are to be re-recorded.

Format 2 files are gzip JSON lines (`.jsonl.gz`) with `"v":2` in `meta`. Recording is armed with
`/blackdoor record <player> [seconds]` and starts when that player rejoins. Besides the events
above it has `chunk` (non-empty sections as `[index, [count, blockStateId, ...]]`, x fastest, then
z, then y) and `unload`; `teleport` with position, rotation and relative bits, and `confirm`;
`tick`, `input`, `action`, `flying`; `abilities`, `gamemode`, `attribute`, `effect` and
`effectRemoved`.

Coverage gaps to fill later: falls from greater heights, elytra, riptide, vehicles, effects (speed,
jump boost, slow falling), soul sand / honey / slime / cobweb / powder snow, and older clients
(1.8.8, 1.21.x) via ViaVersion or native servers.
