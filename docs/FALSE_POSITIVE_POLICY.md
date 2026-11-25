# False-Positive Prevention Policy

> **Phase:** 0 · **Status:** Policy draft for review. Binding on all checks once approved.

## 1. Rules

1. **No evidence, no verdict.** When required state is missing or uncertain, a check returns
   `NO_VERDICT`. Absence of evidence is never a violation.
2. **Monitor first.** New checks ship disabled or monitor-only. Promotion requires passing the FP
   corpus and a staged rollout (DETECTION_REGISTRY.md §9).
3. **Confidence gates punishment.** Statistical checks cannot punish. Heuristic checks cannot
   kick/ban alone. Automatic kick/tempban requires Deterministic/High findings, correlated where
   configured, with no active uncertainty flags.
4. **Narrow exemptions, never blanket ones.** Exemptions are scoped (check or category),
   conditional (e.g., "while flight ability granted"), and time-bounded by default.
5. **Explainability.** Every finding carries the observed value, the allowed bound, and the
   uncertainty terms applied.

## 2. Uncertainty Flags (attached to findings)

| Flag | Raised when | Effect |
|---|---|---|
| `LAG_CLIENT` | RTT/jitter above threshold or ack backlog | Timing checks widen; enforcement suppressed above hard threshold |
| `LAG_SERVER` | MSPT above threshold / TPS below threshold | Timing checks widen; timer checks pause |
| `PACKET_BURST` | Batched arrival after silence | Timer balance catch-up allowed |
| `TELEPORT_PENDING` | Teleport not yet confirmed | Movement checks skip |
| `RESPAWN/WORLD_CHANGE` | Recent | Grace window |
| `VELOCITY_PENDING` | Unacked server velocity | Either-or evaluation |
| `BLOCK_CHANGE_PENDING` | Unacked block changes near player | Evaluate both states |
| `UNKNOWN_WORLD` | Chunk not in view model | Movement checks `NO_VERDICT` |
| `TRANSLATED` | Client version ≠ server version (Via) | Version-specific tolerances; some checks disabled per feature table |
| `BEDROCK` | Floodgate/Geyser player | Movement/combat prediction disabled by default |
| `UNSUPPORTED_VERSION` | Version not onboarded | Prediction disabled |
| `VEHICLE` | Riding | Movement checks replaced by vehicle sanity checks |
| `EXTERNAL_MOVEMENT` | Plugin-applied velocity/teleport observed via API or packets | Treated as pending velocity |
| `CUSTOM_ITEM` | Item with configured custom marker | Item-based checks follow item policy |

## 3. Exemption Sources

| Source | Scope | Default duration |
|---|---|---|
| Permission (`blackdoor.exempt.<check-id>`) | Per check; no wildcard grant of all checks via a single node by design | While permission held |
| Game mode (creative/spectator) | Movement & certain world checks | While in mode |
| Abilities (`allowFlight`, flying) | Fly-family checks | While ability held |
| API exemption (third-party plugin) | Check/category, logged with plugin name | Capped (e.g., ≤ 10 s per call) unless operator raises cap |
| Staff command | Per player, per check | Required TTL |
| Config per world | Per check per world (e.g., parkour world) | Permanent but explicit in config |

Permanent all-check exemptions for a player are not available via API or command; only via
explicit config, which logs a startup warning.

## 4. Known Legitimate Scenarios To Test (FP corpus seeds)

High-ping (300–600 ms) and jittery connections; packet loss; TPS 10–15; elytra flight with
fireworks; riptide in rain; ice-boat travel; bubble columns; slime/honey parkour; knockback chains
and explosions; wind charges and mace (modern); levitation/slow falling; vehicles of each type;
swimming/crawling; ladders/scaffolding; cobweb/powder snow; teleport-heavy minigames; plugin
launch pads; NPC plugins; Purpur config tweaks; ViaVersion/ViaBackwards/ViaRewind clients;
Geyser players; legitimate client mods (performance mods, replay mods); high-CPS clicking
techniques; controller players.

## 5. Process

- Every reported FP becomes a regression test (recorded session replay where possible).
- Staff review tooling shows evidence and lets reviewers mark `false-positive`; FP marks feed
  check statistics (FP rate per check) and can auto-demote a check to monitor mode if the rate
  crosses a configured threshold.
- After every Minecraft update, prediction checks stay disabled for the new version until
  onboarding completes.
