# Black Door

Server-side security framework for Minecraft Java Edition: packet security, server-authoritative
movement and combat validation, exploit prevention, application-layer connection protection,
and explainable enforcement.

**Status: Phase 1 — foundation build. No security protections are active yet. Nothing here is
production-ready.** Live progress: [IMPLEMENTATION.md](IMPLEMENTATION.md).

Start with [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md).

## Building

Requires a JDK 21+ to run Gradle; Gradle provisions the JDK 25 build toolchain automatically.

```sh
./gradlew build
```

Artifacts:

| Jar | Platforms |
|---|---|
| `dist-bukkit/build/libs/BlackDoor-Bukkit-<ver>.jar` | Bukkit, Spigot, Paper, Purpur, Folia (Java 17+) |
| `blackdoor-proxy-velocity/build/libs/BlackDoor-Velocity-<ver>.jar` | Velocity 4.x (Java 25) |
| `blackdoor-proxy-bungee/build/libs/BlackDoor-Bungee-<ver>.jar` | BungeeCord (Java 17+) |

On Bukkit-family servers, install [PacketEvents](https://github.com/retrooper/packetevents) as a
separate plugin. Without it Black Door starts in a DEGRADED state.

## License

GPL-3.0. See [LICENSE](LICENSE).

Black Door does not claim complete cheat detection or complete DDoS protection. Volumetric
attacks require upstream network mitigation; see [docs/DDOS_LIMITATIONS.md](docs/DDOS_LIMITATIONS.md).
