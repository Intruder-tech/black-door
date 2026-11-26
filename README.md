# Black Door

**Made in India. Guard your world.**

Server security for Minecraft Java Edition servers and proxies: packet checks, exploit guards, join
and ping flood protection, staff tools for suspects, and an in-game control panel.

**Status: beta (`1.0.0-beta.1`).** Try it on a test server first and keep checks on Watch until you
have seen what they report. See [CHANGELOG.md](CHANGELOG.md) for how far it has been tested.

> **Black Door is not a movement or combat anti-cheat.** It does not detect fly, speed, reach, kill
> aura or similar cheats. Use it alongside an anti-cheat if you need that.

## What it does

- **Packet checks** for data a normal game client does not send: impossible positions and head
  angles, invalid hotbar slots, self-interaction, fake teleport and connection replies, oversized
  messages and packet flooding.
- **Exploit guards** for oversized mod data, crash books, crash signs and illegal creative items.
- **Join and ping flood protection** on servers, Velocity and BungeeCord.
- **Bot flood mode:** during a flood of logins, addresses that played before keep getting in.
- **Auto action** for players who keep being blocked: watch, quarantine (move and chat only) or
  kick, with optional temporary bans.
- **Cases** for staff review of every action, with a false alarm verdict that undoes it.
- **Login system** (optional): `/signup` and `/signin` with hashed passwords and a 24-hour
  same-address session. See [docs/AUTHENTICATION.md](docs/AUTHENTICATION.md).
- **Anti X-Ray switch** for Paper's own anti-xray.
- **In-game control panel** with plain names for every setting, and an audit log.

Everything that can affect players is off or watch-only until you turn it on. Something is blocked
only when it is set to Block *and* global protection is on.

Black Door cannot guarantee that a server cannot be breached and cannot absorb volumetric DDoS
attacks; see [docs/DDOS_LIMITATIONS.md](docs/DDOS_LIMITATIONS.md) and
[docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).

## Installing

Download the jar for your platform from the
[releases page](https://github.com/Intruder-tech/black-door/releases).

| Jar | Platforms | Also needs |
|---|---|---|
| `BlackDoor-Bukkit-<ver>.jar` | Bukkit, Spigot, Paper, Purpur, Folia (Java 17+) | [PacketEvents](https://github.com/retrooper/packetevents) |
| `BlackDoor-Velocity-<ver>.jar` | Velocity 4.x (Java 25) | |
| `BlackDoor-Bungee-<ver>.jar` | BungeeCord (Java 17+) | |

1. Put the jar (and PacketEvents on a server) in `plugins/` and start. Without PacketEvents a server
   runs in a DEGRADED state with no packet or join checks.
2. Run `/blackdoor setup` and fix every FAIL line.
3. Run `/blackdoor gui`. Leave checks on Watch until you have reviewed what they report, then set
   them to Block and turn Protection on.

Proxies have no panel: edit `network.yml` and `config.yml`, then run `/blackdoorproxy reload`.

## Commands

| Command | Use |
|---|---|
| `/blackdoor setup [profile]` | First-time security check |
| `/blackdoor gui` | Control panel |
| `/blackdoor status` / `checks` | Health, checks and finding counts |
| `/blackdoor player <name>` | Inspect one player |
| `/blackdoor quarantine [<player> \| release <player>]` | Hold or release a suspect |
| `/blackdoor cases [number] [false\|real]` | Review actions taken |
| `/blackdoor bans [unban <player>]` | Temporary bans |
| `/blackdoor reload` | Reload configuration |
| `/blackdoor help` | Full list |

The permission `blackdoor.exempt.kick` (given to nobody by default) exempts a player from auto
action. Settings are described in [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Building

Requires a JDK 21+ to run Gradle; Gradle provisions the JDK 25 build toolchain automatically.

```sh
./gradlew build
```

Jars are written to `dist-bukkit/build/libs/`, `blackdoor-proxy-velocity/build/libs/` and
`blackdoor-proxy-bungee/build/libs/`.

Start with [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) for the design. Report security
problems as described in [SECURITY.md](SECURITY.md).

## License

GPL-3.0. See [LICENSE](LICENSE).

Not affiliated with or endorsed by Mojang or Microsoft.
