# Black Door

**Made in India. Guard your world.**

A security framework for Minecraft Java Edition servers and proxies: packet checks, exploit
guards, join and ping flood protection, and an in-game control panel. Server-authoritative
movement and combat validation is planned.

**Status: early development (`0.1.0-SNAPSHOT`). Not production-ready.** Live progress:
[IMPLEMENTATION.md](IMPLEMENTATION.md).

## What works today

- **Packet checks** for data a normal game client does not send: impossible positions and head
  angles, invalid hotbar slots, self-interaction, fake teleport and connection replies, oversized
  messages and packet flooding.
- **Exploit guards** for oversized mod data, crash books, crash signs and illegal creative items.
- **Join and ping flood protection** on backend servers, Velocity and BungeeCord.
- **In-game control panel** (backend servers) with plain names for every check.
- **Safe by default:** everything starts in watch mode. Something is blocked only when it is set
  to Block *and* global protection is on.
- **Audit log** of administrative changes.

## Not built yet

Fly, speed, reach and combat checks, anti X-ray / ESP, bot scoring, automatic punishments and a
proxy control panel. See [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md).

Black Door does not claim complete cheat detection, cannot guarantee that a server cannot be
breached, and cannot absorb volumetric DDoS attacks; see
[docs/DDOS_LIMITATIONS.md](docs/DDOS_LIMITATIONS.md).

## Installing

| Jar | Platforms | Also needs |
|---|---|---|
| `BlackDoor-Bukkit-<ver>.jar` | Bukkit, Spigot, Paper, Purpur, Folia (Java 17+) | [PacketEvents](https://github.com/retrooper/packetevents) |
| `BlackDoor-Velocity-<ver>.jar` | Velocity 4.x (Java 25) | |
| `BlackDoor-Bungee-<ver>.jar` | BungeeCord (Java 17+) | |

1. Put the jar (and PacketEvents on a backend) in `plugins/` and start the server. Without
   PacketEvents a backend runs in a DEGRADED state with no packet or join checks.
2. Run `/blackdoor setup` and fix every FAIL line.
3. Run `/blackdoor gui`. Leave checks on Watch until you have reviewed what they report, then set
   them to Block and turn Protection on.

Proxies have no panel: edit `network.yml` and `config.yml`, then run `/blackdoorproxy reload`.

The newest features have been tried live on Paper 26.3, Velocity and BungeeCord only.

## Commands

| Command | Use |
|---|---|
| `/blackdoor setup [profile]` | First-time security check |
| `/blackdoor gui` | Control panel |
| `/blackdoor status` / `checks` | Health, checks and finding counts |
| `/blackdoor player <name>` | Inspect one player |
| `/blackdoor reload` | Reload configuration |
| `/blackdoor help` | Full list |

Settings are described in [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Building

Requires a JDK 21+ to run Gradle; Gradle provisions the JDK 25 build toolchain automatically.

```sh
./gradlew build
```

Jars are written to `dist-bukkit/build/libs/`, `blackdoor-proxy-velocity/build/libs/` and
`blackdoor-proxy-bungee/build/libs/`.

Start with [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) for the design.

## License

GPL-3.0. See [LICENSE](LICENSE).

Not affiliated with or endorsed by Mojang or Microsoft.
