# Known Limitations

> **Phase:** 0 · Kept current through all phases. Each item: limitation → reason → mitigation (if any).

## Detection
1. **Undetectable client-only cheats.** ESP, tracers, freecam viewing, minimap/radar and
   X-ray *viewing* produce no server evidence. Only prevention (not sending data) helps.
2. **Human-like automation.** Well-randomized autoclickers, aim assist and macros can be
   statistically indistinguishable from skilled humans. These checks are review-only.
3. **Bedrock players.** Java physics prediction does not apply to Geyser-translated movement.
4. **Vehicles.** Weak coverage until a later phase.
5. **New Minecraft versions.** Prediction disabled until onboarded; protocol/exploit guards stay on.
6. **Custom gameplay plugins.** Physics changes by other plugins (Purpur options, launch pads,
   custom enchantments) must be declared/exempted; Black Door cannot infer them all.
7. **Zero-day exploits.** Guards cover known classes; unknown crash/dupe vectors may exist.

## Platform
8. **Java 17+ runtime required** (ADR-002; pending OD-1). Java-8-only legacy servers unsupported.
9. **Same-JVM plugins are trusted.** A malicious plugin can bypass any in-process protection.
10. **Modded servers** (Fabric/Forge/NeoForge) unsupported.
11. **Waterfall** is end-of-life upstream; best-effort only. **Velocity 3.x** is not targeted:
    the Velocity adapter is built against Velocity API 4.x and needs Java 25.
12. **BungeeCord legacy forwarding** cannot be made secure by a plugin alone; requires network
    isolation or a token guard.

## Network
13. **No volumetric DDoS protection** (DDOS_LIMITATIONS.md).
14. **Shared IPs** (CGNAT, VPNs) limit how aggressive IP-based throttling can be.
15. **Distributed botnets** with many residential IPs can evade per-IP limits; adaptive mode then
    affects legitimate joins as well.

### Current implementation gaps (connection protection and exploit guards)

- `allowed-proxies` compares exact addresses (no ranges) and needs PacketEvents; a firewall is
  still the stronger protection. BungeeGuard-style tokens are not checked.
- BungeeCord cannot refuse a status ping event, so pings there are limited only by the
  connection limit (the `pings` limit has no effect).
- Velocity cannot refuse at handshake: its connection and hostname limits apply when the ping or
  login arrives. BungeeCord does not check hostname length.
- Proxies have no in-game panel: change `network.yml` and `config.yml`, then
  `/blackdoorproxy reload`.
- A proxy behind another proxy or TCP shield without PROXY protocol sees one shared address; list
  it in `exempt-addresses`.
- Velocity modern forwarding cannot be detected from the backend. A proxy reaching the backend
  over a public address must be listed in `exempt-addresses`.
- Book edits on pre-1.13 clients arrive as plugin messages and are only size-checked.
- Client brand sent during the configuration state (1.20.2+) is not inspected.
- Item data is limited by packet size only; nesting depth and component counts are not checked.
- Creative actions are not yet compared with the player's actual game mode.

## Engineering
16. **World view memory** at high player counts is unresolved (OD-3).
17. **PacketEvents dependency** on the critical path; upgrades must be reviewed per release.
18. **License coupling** with GPL-3.0 dependencies (OD-2).

## Unresolved Architectural Decisions
See [ARCHITECTURAL_REVIEW.md §7](ARCHITECTURAL_REVIEW.md) (OD-1 … OD-9).
