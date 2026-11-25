# DDoS Limitations

> **This document is deliberately blunt.** Black Door is not DDoS protection in the sense most
> hosting providers use the term.

## 1. What a Plugin Can Never Stop

| Attack | Why a plugin cannot stop it | What does |
|---|---|---|
| Volumetric floods (UDP, amplification, GRE, etc.) | Saturate the link before packets reach the JVM | Provider scrubbing / upstream filtering |
| SYN floods / spoofed TCP | Handled by the kernel before userspace sees a connection | SYN cookies, provider filtering, kernel tuning |
| Bandwidth exhaustion with valid TCP | Link is full regardless of what the application does | Upstream capacity, anycast/scrubbing |
| Attacks on the proxy host's other services | Out of process | Host firewall |
| Attacks on DNS / upstream infrastructure | Out of scope | Provider |

## 2. What Black Door Can Help With

| Attack | Mitigation | Limit |
|---|---|---|
| Connection floods with completed TCP handshakes | Early rate limiting, quick close | Each connection still costs kernel + Netty resources |
| Server-list ping floods | Ping throttling, cached responses | Same |
| Login floods / bot joins | Login throttling, quarantine, adaptive mode | Distributed botnets with many residential IPs can stay under per-IP limits; adaptive global limits then also affect legitimate players |
| Packet floods from joined clients | Per-type and per-byte budgets, disconnect | — |
| Expensive-packet attacks (crash/lag payloads) | Exploit guards before parsing | Unknown vectors |
| Repeat offenders | Optional edge agent pushes temporary drops into nftables | Only as good as the kernel's capacity; not upstream |

## 3. Operational Recommendations

1. Use a host or provider with network-level DDoS mitigation for any public server.
2. Run a proxy (Velocity recommended) and firewall backends so they accept only proxy IPs.
3. Enable SYN cookies and sensible conntrack limits on the host.
4. Treat Black Door's network module as an application-layer filter, not a shield.

## 4. Claims Policy

Black Door documentation, configs and messages must never describe the product as providing
complete or volumetric DDoS protection.
