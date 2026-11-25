# Protocol Architecture & Version Compatibility Strategy

> **Phase:** 0 · **Status:** Proposed design · **Last updated:** 2026-11-26

## 1. Two Versions Per Player

Every player session carries two independent versions:

| Version | Source | Governs |
|---|---|---|
| `ClientVersion` | PacketEvents (which reads ViaVersion when present), or proxy-provided | Movement physics, client-side mechanics (input packet, 0.03 behavior, swim/crawl, elytra), packet cadence, rotation precision |
| `ServerVersion` | Platform at startup | World rules (block shapes as the server simulates them), combat rules (cooldown), item format (NBT vs. components), connection states |

When they differ (Via translation), checks consult a **feature table**, never raw version numbers:

```java
// Illustrative only — not implemented.
interface ProtocolFeatures {
    boolean hasIdleFlyingPacket();         // client ≤ 1.8
    boolean usesPingPongTransactions();    // client ≥ 1.17 (and server-side support)
    boolean hasDirectionalInputPacket();   // client ≥ 1.21.2 [verify]
    boolean hasConfigurationState();       // connection ≥ 1.20.2
    boolean usesItemComponents();          // server ≥ 1.20.5
    boolean hasAttackCooldown();           // server ≥ 1.9 (and client ≥ 1.9 for UI)
    MovementModel movementModel();         // client-version-specific physics implementation
}
```

Feature flags are derived from registries supplied by the packet library; adding a version means
adding a row of feature data plus tests, not editing every check.

## 2. Packet Abstraction

- `PacketSource` SPI delivers `BdPacket` views: type, direction, connection state, wire size,
  decoded fields via typed accessors. Large payloads (custom payload bytes, chunk data) are
  exposed lazily and size-checked before decoding.
- Only `blackdoor-protocol-packetevents` imports PacketEvents. Replacing the library means
  reimplementing that module.
- Black Door registers its listener at a defined priority and **does not mutate** packets of other
  plugins except: dropping invalid packets, and sending its own transaction packets.

## 3. Connection State Machine

States: `HANDSHAKE → STATUS` or `HANDSHAKE → LOGIN → (CONFIGURATION ⇄ PLAY)` (configuration
state on ≥ 1.20.2; PLAY may re-enter CONFIGURATION).

For each state, a table defines: allowed serverbound packet types, max count before transition,
max wire size, and the expected next state. Any packet outside the table ⇒ drop + record; repeated
⇒ disconnect (configurable). The table is generated per protocol version from registry data and
reviewed by tests that run real clients through every transition.

## 4. Packet Security Engine (`blackdoor-packets`)

Ordered stages; each stage is cheap and runs before anything more expensive:

| Stage | Rules | Cost |
|---|---|---|
| 1. Byte/frame budget | Per-connection bytes/s and frames/s token buckets; global per-IP aggregate | O(1) |
| 2. Size | Max wire size per packet type and state (tighter than vanilla where safe) | O(1) |
| 3. State | Allowed in current state & direction | O(1) table lookup |
| 4. Rate | Per-type token buckets (e.g., movement ≤ expected tick rate + jitter allowance, interact, chat, commands, tab-complete, custom payload, book edit) | O(1) |
| 5. Order | Sequencing rules: e.g., no attack before login complete; teleport-confirm IDs match pending teleports; transaction responses match sent IDs in order; dig start/finish pairing | O(1) amortized |
| 6. Field bounds | Finite doubles (no NaN/Inf); coordinate magnitude within world border + margin; pitch ∈ [-90, 90]; slot indices within container; entity IDs known to client; string length limits; enum ordinals valid | O(fields) |
| 7. Payload structure | Custom payload channel allowlist & size; book/sign/item structural limits (delegated to exploits module) | bounded |

Outcomes: `PASS`, `DROP`, `DROP_AND_FLAG`, `DISCONNECT`. Disconnects are rate-limited per IP to
avoid amplification of reconnect loops.

**Backpressure:** if a player's inbound queue (as seen by PacketEvents/Netty) exceeds a budget,
Black Door stops reading that channel's auto-read briefly (Netty `setAutoRead(false)`), and
disconnects if sustained — **[verify]** interaction with PacketEvents and Via before adopting.

## 5. Transactions

- 1.17+ clients: Ping/Pong packets with reserved ID range.
- ≤ 1.16 clients: window-confirmation packets on window 0 with reserved negative action numbers
  (convention used by prediction anti-cheats) **[verify]** per version and against Via
  translation (a 1.8 client on a 1.21 server via ViaRewind).
- Missing acknowledgements beyond a time/count budget ⇒ uncertainty flag; extreme backlog ⇒
  configurable kick (players who never ack are either broken or manipulating timing).

## 6. Version Onboarding Procedure (each new Minecraft release)

1. Update PacketEvents/Via pins; run full CI matrix.
2. Diff Mojang changelog + decompiled client physics (reading only, for behavior specification;
   no redistribution) for: movement, collision shapes, combat, items, connection states.
3. Add feature-table row + block-shape registry data.
4. Run physics golden tests (recorded legitimate movement) for the new version.
5. Run FP corpus replay; any regression blocks release.
6. Update `compatibility-matrix.yml` only after the above pass.

Until onboarding completes, a newer client/server is treated as **unsupported version**:
protocol and exploit guards stay active, prediction checks are disabled for it, and staff are
notified once at startup.

## 7. Proxy Considerations

- On a proxy, packets are seen before the backend; Black Door on the proxy handles network-layer
  throttling and protocol-level validation only.
- Detection runs on the backend, because only the backend has the world and physics context.
- Cross-server state (e.g., quarantine, recent violations) shared via authenticated channel
  (plugin messaging is **not** trusted for this; see NETWORK_SECURITY.md §4).
