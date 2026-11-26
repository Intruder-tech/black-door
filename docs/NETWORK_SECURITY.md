# Network Security Architecture

> **Phase:** 0 · **Status:** Proposed design · **Implementation phase:** 4
> Read with [DDOS_LIMITATIONS.md](DDOS_LIMITATIONS.md).

## 1. Layers

| Layer | Where | What Black Door does | What it cannot do |
|---|---|---|---|
| **C. External network** | Provider scrubbing, host kernel, firewall | Optional `blackdoor-edge` pushes bounded, expiring block entries (opt-in) | Absorb bandwidth floods, filter spoofed SYNs, protect upstream links |
| **B. Proxy** | Velocity / BungeeCord | First application-layer line: handshake/ping/login throttling, bot heuristics, distributed quarantine | Anything before the proxy accepts TCP |
| **A. Application (backend)** | Paper/Spigot/Folia | Same throttles when no proxy; packet-rate limits; forwarding validation; session quotas | Same |

## 2. Application-Layer Protection (`blackdoor-network`)

### 2.1 Connection lifecycle hooks
- Earliest available hook per platform: Netty channel initialization (via packet library) for
  connection counting; handshake packet for intent/hostname/protocol validation; status
  requests for ping throttling; login start for name/UUID checks. **[verify]** exact hook
  availability on each platform in Phase 4 spike.
- Platform events (e.g., pre-login events) are used as a second line, not the first.

### 2.2 Rate limiting
- Algorithm: **token bucket** (O(1), burst-tolerant) per key.
- Keys: per IP; per IPv4 /24 and IPv6 /48 (configurable) as **soft** aggregate; global.
- Buckets: new connections, status pings, login attempts, failed logins, handshake-only
  disconnects, reconnects per account.
- Storage: bounded concurrent map (striped), LRU/idle eviction, hard cap (default e.g. 100k keys),
  memory estimated and displayed in `/blackdoor network`.

### 2.3 Adaptive mode
**Built (2026-11-26):** a fixed trigger on logins from unknown addresses; during an attack,
addresses that played before get in and the rest share a small allowance
([design](superpowers/specs/2026-11-26-adaptive-mode-design.md), CONFIGURATION.md).
The points below are the wider Phase 0 sketch; the baseline, per-prefix limits and extra
join checks are not built.

- Baseline measured with EWMA over normal operation.
- When connection rate exceeds baseline × factor (or an absolute threshold), switch to
  **under-attack** mode: tighter per-prefix limits, temporarily require that new IPs pass extra
  checks (e.g., delayed join, ping-before-join verification **[evaluate]**), prioritize known
  recent-good IPs (bounded "seen-good" cache of IPs that completed a session).
- Exit after sustained normal rate; all transitions logged and alerted.

### 2.4 Handshake & login validation
- Protocol version known and allowed; next-state valid; hostname length/charset; port sanity.
- Login name format per version; duplicate in-flight logins for same name/UUID limited.
- Timeouts: handshake→login and login→play stage timeouts tighter than vanilla (configurable).
- Online-mode authentication is never bypassed or weakened.

### 2.5 Bot detection (heuristic, explainable)
Signals combined into a score shown to staff, each with its reason:
connection burst membership; protocol/hostname anomalies; client brand absence or anomalies
(spoofable — weak signal only); join-without-movement; identical behavior across accounts;
failed transaction responses. Outcome: throttle → quarantine (limbo-like restrictions) → kick.
**No automatic permanent IP bans.**

### 2.6 Quarantine
Session-scoped restrictions (no chat/commands/interaction, or hold at join) with TTL. Released by
time, by passing verification, or by staff. Designed to be safe for false positives.

### 2.7 Shared networks
CGNAT, universities, VPNs and proxies mean many legitimate players share IPs. Therefore:
per-prefix limits are throttles, not bans; ban actions target accounts, not IPs, by default;
IP-based actions are temporary and capped; allowlist for known ranges/proxies.

## 3. Packet-Level Limits
See PROTOCOL_ARCHITECTURE.md §4 — per-connection byte/frame budgets and per-type rates run on
both proxy and backend.

## 4. Proxy Security

### 4.1 Forwarding modes
| Mode | Authentication | Black Door behavior |
|---|---|---|
| Velocity modern | HMAC with shared secret | Verify server is configured for it; refuse to start enforcement features if secret is default/empty; warn on weak secrets |
| BungeeGuard-style token | Shared token in handshake | Verify presence; recommend |
| Legacy BungeeCord / Velocity legacy | **None** | Loud startup warning; check whether backend is bound to a public interface; recommend firewalling backend to proxy IPs; optional built-in source-IP allowlist (proxy IPs only) |
| No proxy (online-mode backend) | Mojang session | Normal |

### 4.2 Distributed event sharing
- Purpose: share quarantines, network-attack status, and violation summaries across servers.
- Transport options: SQL (polling), Redis/Valkey pub/sub (Phase 6), or authenticated proxy channel.
- **Plugin messaging is not used for security decisions** because any player connection carries
  plugin channels and the data is only as trustworthy as the sender; if used for transport, every
  message is HMAC-signed with a network secret, timestamped, nonce-deduplicated and size-bounded.

## 5. External Network Protection (`blackdoor-edge`, optional)

```text
 Plugin ──(Unix socket, 0600, HMAC-signed JSON: {op, ip, ttl, reason, nonce, ts})──► blackdoor-edge
                                                                         │ runs as dedicated user
                                                                         │ with CAP_NET_ADMIN only
                                                                         ▼
                                            nftables: pre-created set `blackdoor_block` (timeout flag)
                                                  add element { <ip> timeout <ttl> }
```

- Disabled by default; requires the administrator to install the agent separately and configure
  the shared key. The plugin cannot install or enable it.
- Operations: `block`, `unblock`, `list`, `health`. No other commands. IP literals validated
  (no hostnames, no ranges unless explicitly enabled with a max prefix size).
- TTL capped (default max e.g. 1 hour); max set size; global new-rule rate limit.
- Never blocks allowlisted ranges (proxy IPs, admin ranges).
- No shell: uses nftables via netlink library or a fixed argv invocation with validated arguments.
- iptables/ipset backend optional; XDP/eBPF and provider APIs (e.g., hosting firewall APIs) are
  adapter interfaces only — design in later phase, each requiring least-privilege credentials.
- Both sides keep an audit log.

## 6. Default Settings Philosophy

Defaults must be safe for a 500-player event start (surge) on a shared-NAT population: throttles
are generous, adaptive mode triggers on anomalies, no IP bans. Profiles (CONFIGURATION.md)
provide stricter presets for large networks.
