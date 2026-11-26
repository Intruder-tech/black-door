# Configuration Architecture

> **Phase:** 0 · **Status:** Design outline. Implementation in Phase 1 (framework) and later
> phases (per-subsystem keys). Key names below are illustrative, not final.

## 1. Files

| File | Contents |
|---|---|
| `config.yml` | `config-version`, active profile, mode (`monitor`/`enforce`), language, general toggles |
| `checks.yml` | Per-check: enabled, mode, thresholds, weight, decay, mitigations, per-world overrides |
| `network.yml` | Rate limits, adaptive mode, quarantine, proxy/forwarding expectations, allowlists |
| `exploits.yml` | Guard toggles and limits, custom-item allowlists |
| `enforcement.yml` | Policies (thresholds → actions), cooldowns, `allow-permanent-bans: false` |
| `storage.yml` | Backend, connection (secrets via `${env:VAR}` or `${file:path}`), retention |
| `integrations.yml` | Via*, Geyser/Floodgate, anti-xray, webhooks, metrics endpoint, edge agent |
| `messages.yml` | MiniMessage-formatted user/staff messages |

## 2. Mechanics

- **Schema validation:** every file is mapped to typed objects with constraints (ranges, enums,
  required keys). Errors report file, path and reason.
- **Versioned migrations:** `config-version` per file; ordered migration steps; backup written
  before migrating (`<file>.bak-<version>`).
- **Atomic reload:** parse + validate all files → build immutable config snapshot → swap
  reference. Any error ⇒ keep previous snapshot, report errors (no partial apply).
- **Rollback:** last-known-good snapshot retained; `/blackdoor reload` reports diff summary.
- **Per-world overrides:** `worlds.<name>.checks.<id>`.
- **Permission exemptions:** `blackdoor.exempt.<check-id>` (see FALSE_POSITIVE_POLICY.md §3).
- **Environment profiles:** `profile: survival | smp | competitive-pvp | minigames | creative |
  custom | proxy-network`. A profile is a base layer; files override it.
- **Safety:** YAML alias/depth/size limits; paths confined to data folder; secrets redacted in
  diagnostics.

## 2a. Implemented in Phase 1

Only `config.yml` exists so far. The other files are added by the phases that need them.

| Key | Type / range | Default | Effect today |
|---|---|---|---|
| `config-version` | integer | `1` | Migration control. Do not edit. |
| `profile` | `survival` `smp` `competitive-pvp` `minigames` `creative` `custom` `proxy-network` | `survival` | Recorded only. |
| `mode` | `monitor` / `enforce` | `monitor` | `enforce` logs a warning: no enforcement exists yet. |
| `language` | `xx_YY` | `en_US` | Only `en_US` exists; other values log a warning. |
| `workers.threads` | 1–4 | `1` | Background I/O threads (restart required). |
| `workers.queue-capacity` | 64–65536 | `1024` | Bounded work queue (restart required). |
| `commands.reload-cooldown-seconds` | 0–3600 | `5` | Minimum time between reloads. |
| `audit.enabled` | boolean | `true` | Append-only `audit/audit-YYYY-MM.log` (JSON lines, UTC). |

Behavior verified by unit tests:
- Unknown keys produce warnings (typo detection), not errors.
- Any invalid value makes the whole reload fail. The previous configuration stays active, or the
  bundled defaults if no valid file was ever loaded (the plugin is then DEGRADED).
- A file newer than the build supports is refused, never downgraded.
- YAML alias bombs, arbitrary-type tags (`!!javax...`), duplicate keys, nesting deeper than 32
  levels and files over 1 MiB are rejected.

## 2b. Implemented in Phase 2 (milestone 2.1)

**`checks.yml`:** `packet.<check>.mode` (`disabled` / `monitor` / `mitigate`) for invalid-position,
invalid-rotation, invalid-slot, self-interact, teleport-confirm, oversized and flood. Also the
oversized limits, the flood token buckets (`burst`, `per-second`; changes apply to new
connections) and `packet.max-sessions` (restart required).

**`config.yml`:** new key `setup.completed`, set by `/blackdoor setup`.

**In-game configuration** (no file editing needed):

1. `/blackdoor setup [profile]` runs the initial security check and finishes setup.
2. `/blackdoor gui` opens the control panel: the global mode and per-check modes are click-to-cycle.
3. Each change is applied by rewriting only that value (comments stay), validated, written
   atomically, reloaded and audited (`audit/audit-YYYY-MM.log`).

| Permission | Allows |
|---|---|
| `blackdoor.command.setup` | `/blackdoor setup` |
| `blackdoor.command.gui` | Open the panel (view) |
| `blackdoor.gui.edit` | Change settings in the panel |
| `blackdoor.command.checks` / `blackdoor.command.player` | Text views |

**Anti X-ray choice** (Paper, Purpur and Folia only; needs `blackdoor.gui.edit`): the "Anti X-Ray"
item in the panel changes Paper's own anti-xray. Each click moves Off > Normal > Strong > Off.

| Choice | `enabled` | `engine-mode` | What players with X-ray see |
|---|---|---|---|
| Off | `false` | not touched | every ore |
| Normal | `true` | `1` | ores buried in rock are hidden; ores touching air stay visible |
| Strong | `true` | `2` | rock is also filled with fake ores (a jumble); costs some bandwidth and client performance |

Neither hides anything above Paper's configured `max-block-height`. An existing `engine-mode: 3`
counts as Strong and is rewritten only if Normal is chosen. If any loaded world is off, the panel
shows Off; it shows Strong only if every world is Strong.

Both keys are under `anticheat.anti-xray`, in `config/paper-world-defaults.yml`, and in a world's
`paper-world.yml` only for keys that file already has. Nothing else is changed. Normal and Strong
need an `engine-mode` key in the defaults file; without one the change is refused. Before the first
change to a file a copy is kept next to it (`<file name>.blackdoor-backup`). A change applies
after the next server restart; Black Door never restarts or reloads the server. Each change is
audited. The panel and `/blackdoor setup` show both what the files say and what the server
started with; the latter is kept in `antixray-startup.properties` in Black Door's data folder
(written by Black Door, not a setting).

## 2c. Implemented in Phase 4 (so far)

**`checks.yml`** exploit guards, each with `mode` (`disabled` / `monitor` / `mitigate`):

| Key under `packet.` | Limits (default) |
|---|---|
| `payload` | `max-bytes` (32767), `max-channel-length` (128), `max-brand-bytes` (256) |
| `book` | `max-pages` (100), `max-page-length` (8192), `max-title-length` (32) |
| `sign` | `max-line-length` (384); more than 4 lines is always flagged |
| `creative-item` | `max-amount` (99), `max-bytes` (65536); slots outside -1..45 are always flagged |
| `item-data` | `max-depth` (32), `max-elements` (8192) of the data in a creative item |
| `game-mode` | No limits: flags creative actions from a player the server put in another game mode (5 seconds of tolerance after leaving creative) |

**`network.yml`** connection protection: `mode`; per-address `connections`, `pings`, `logins`
(`burst`, `per-second`); `max-hostname-length`; `max-tracked-addresses`;
`exempt-private-addresses`; `exempt-addresses`. Limit changes apply to addresses not yet tracked.
Quarantine is not implemented yet.

`network.yml` `adaptive` (login floods from many addresses):

| Key under `adaptive.` | Range | Default | Effect |
|---|---|---|---|
| `mode` | `disabled` / `monitor` / `mitigate` | `monitor` | Own switch, also in the GUI as "Bot flood mode". Refusing needs `mitigate` and `mode: enforce` in `config.yml`. Off when the file's top-level `mode` is `disabled`. |
| `trigger-per-second` | 1–10000 | 10 | Logins per second from unknown addresses, averaged over 10 seconds, that start attack mode |
| `unknown-per-second` | 1–10000 | 2 | Logins per second unknown addresses share during an attack |
| `calm-seconds` | 10–3600 | 60 | Time at or below the trigger before attack mode ends |
| `warmup-seconds` | 0–3600 | 300 | Time after start in which attack mode is noticed but refuses nothing |

An address is known once a player from it has stayed in game for 60 seconds. Exempt addresses,
and backends behind forwarding, are neither counted nor refused.
The same file applies on Velocity and BungeeCord; see KNOWN_LIMITATIONS.md for what each proxy
can refuse.

**`network.yml` `allowed-proxies`** (empty): on a backend behind a proxy, connections from any
other address are logged as `network.direct-join` and, in `mitigate` with global `enforce`,
closed. It complements a firewall and does not replace it. Leave it empty on proxies and on
servers players join directly. `/blackdoor setup` also checks Velocity and BungeeCord forwarding
and flags a short Velocity secret. The proxy flags are read at startup.

## 2d. Login system

**`auth.yml`:** `enabled` (false), `session-hours` (24), `login-timeout-seconds` (60),
`max-attempts` (5), `lockout-minutes` (5), `min-password-length` (8). Permission
`blackdoor.command.auth` allows `/blackdoor auth reset <player>`. See
[AUTHENTICATION.md](AUTHENTICATION.md).

## 2e. Auto kick (Phase 5)

**`enforcement.yml`:** `mode` (`disabled`, `watch` or `kick`; default `disabled`),
`findings-to-kick` (5), `window-seconds` (60). GUI item "Auto kick". Only deterministic and high
confidence findings count, and only blocked ones can kick, so the check must be on Block and
Protection ON. Spam, oversized mod data, books, creative items and item data are heuristic and never
count. Permission `blackdoor.exempt.kick` (default nobody) exempts a player. Kicks are audited.
Backends only.

## 3. Profiles (intent)

| Profile | Intent |
|---|---|
| survival / smp | Balanced; movement & exploit focus; generous combat tolerances |
| competitive-pvp | Tighter combat checks; version-specific combat (1.8 vs. modern) |
| minigames | Teleport/velocity-heavy gameplay tolerances; per-world overrides |
| creative | Movement checks mostly off; strict creative-item exploit guards |
| custom | Minimal defaults; operator configures |
| proxy-network | Network module emphasis; shared-state settings |

All profiles default to **monitor** mode on first install.
