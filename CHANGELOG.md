# Changelog

## 1.0.0-beta.1

First public release. A beta: usable, but not yet proven on busy public servers.

**Black Door has no fly, speed, reach, combat or other movement cheat detection.** It guards the
server against malformed and oversized data, connection floods and account misuse.

### Included

- Packet checks for data a normal game client never sends.
- Exploit guards: oversized mod data, crash books, crash signs, illegal creative items.
- Join and ping flood protection on servers, Velocity and BungeeCord, plus bot flood mode.
- Forwarding checks that block players who try to bypass your proxy.
- Auto action for players who keep being blocked: watch, quarantine or kick, with optional
  temporary bans.
- Saved cases for staff review, with a false alarm verdict that lifts the punishment.
- Optional login system (`/signup`, `/signin`).
- Paper anti-xray status and switch (Paper does the hiding, not Black Door).
- In-game control panel, setup command and audit log.

Everything that can affect players ships off or in watch mode.

### How far it has been tested

- Unit tests and CI.
- Live on local test servers: mainly Paper 26.3 and 1.21.11. Auto kick, bans, cases and quarantine
  were tested with a scripted client on Paper 1.21.11 only.
- Not tested: a busy public server, load, real hacked clients, and most older versions with the
  newest features.

See [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).
