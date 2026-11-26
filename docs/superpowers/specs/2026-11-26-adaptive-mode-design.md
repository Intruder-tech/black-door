# Adaptive mode (bot-flood protection): design

**Date:** 2026-11-26 · **Status:** awaiting owner approval · **Size:** small change

## Intent
During a flood of bot logins from many addresses, regular players keep getting in. Per-address
limits cannot do this, because each bot stays under them.

**Not goals:** bandwidth or SYN floods, IP bans, bot scoring, quarantine, a learned baseline,
changes to ping or handshake limits.
**Failure would be:** real players locked out when no attack is happening.

## Approaches
| Approach | Trade-off |
|---|---|
| A. Tighter per-address limits during an attack | Smallest change, weak against many addresses |
| B. One server-wide login cap | Protects the server, real players compete equally with bots |
| **C. Known players first** (chosen by the owner) | Regulars unaffected, new players may need several tries |

## Behaviour
- **Known address:** one from which a player stayed in game for at least 60 seconds.
- **Start:** logins from unknown addresses average more than `trigger-per-second` over 10 seconds.
- **While active:** known addresses pass as usual. Unknown addresses share one allowance of
  `unknown-per-second` logins. The rest are refused.
- **Warm-up** (added after review, owner's choice): nothing is refused for `warmup-seconds`
  (default 300) after start, so reconnecting regulars are not treated as an attack.
- **End:** the unknown login rate stays at or below the trigger for `calm-seconds`.
- Per-address limits and name checks still apply to everyone. Exempt addresses are neither
  counted nor refused. As with rate limits, a backend behind a forwarding proxy does nothing;
  the proxy does this work.

## Settings (`network.yml`)
```yaml
adaptive:
  mode: monitor          # disabled, monitor, mitigate
  trigger-per-second: 10
  unknown-per-second: 2
  calm-seconds: 60
  warmup-seconds: 300
```
`monitor` reports and refuses nothing. Refusing needs `mitigate` and `mode: enforce` in
`config.yml`. If the network `mode` is `disabled`, adaptive mode is off too.

## Visible to staff
- A finding when attack mode starts, and refusals reported at most once per second.
- A log line when it starts and when it ends.
- Diagnostics row and GUI Status line: active or not, known addresses, refused count.
- GUI item to switch Off / Watch / Block. Proxies have no panel and use the file.

## Where it goes
- `blackdoor-network`: the trigger, the allowance and a bounded known-address list, inside or
  beside `ConnectionGuard`.
- Bukkit, Velocity and BungeeCord adapters: report when a player joins and leaves, with the
  address. This signal does not exist yet.
- `blackdoor-core`: settings, GUI item, diagnostics.

## Known limits
- The known list is in memory. After a restart or reload of the plugin everyone is unknown.
- A player whose address changed counts as unknown.
- On offline-mode servers a patient bot can stay 60 seconds and become known.
- There is no timer: the end of an attack is noticed at the next connection or status view.
- Backend refusals close the connection without a message; proxies show the usual refusal text.

## Verification
Unit tests with a manual clock. A live localhost run with `exempt-private-addresses: false`,
since local addresses are exempt by default.

## Left to the builder
Class layout, list size (default: reuse `max-tracked-addresses`) and wording of messages.
