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
