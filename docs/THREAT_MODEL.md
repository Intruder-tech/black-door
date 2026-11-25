# Threat Model (STRIDE)

> **Phase:** 0 · **Status:** Draft for review · **Method:** STRIDE per trust boundary
> Re-reviewed at the end of every phase; changes logged in §7.

## 1. Assets

| ID | Asset | Why it matters |
|---|---|---|
| A1 | Server availability (TPS, memory, connections) | Primary operational asset. |
| A2 | Game integrity (fair play, economy, items) | Purpose of the anti-cheat. |
| A3 | Player identity (UUID, name, forwarded IP) | Impersonation enables griefing and bans of innocents. |
| A4 | Enforcement integrity (bans, quarantines, exemptions) | Abuse can silence or ban players unjustly. |
| A5 | Evidence & violation records | Personal data; needed for appeals. |
| A6 | Secrets (DB credentials, forwarding secret, webhook URLs/tokens, edge-agent key) | Compromise extends attack beyond the server. |
| A7 | Host system (firewall, filesystem) | Edge agent touches privileged surfaces. |
| A8 | Black Door code & build pipeline | Supply-chain compromise turns a security tool into a backdoor. |

## 2. Actors

| Actor | Trust |
|---|---|
| Anonymous network client (pre-login) | Untrusted |
| Authenticated player (possibly cheating) | Untrusted input, known identity |
| Bot operator / attacker with many IPs | Untrusted, high volume |
| Staff member | Partially trusted, permission-scoped |
| Server administrator | Trusted for configuration |
| Other plugins on the same server | Same JVM — cannot be isolated; treated as trusted-but-buggy |
| Proxy | Trusted only if authenticated (Velocity modern forwarding) or network-isolated |
| External services (DB, Redis, webhook endpoint, metrics scraper) | Semi-trusted; authenticated |
| Upstream dependency maintainers | Trusted via pinning, review and verification |

## 3. Trust Boundaries

```text
 TB1  Internet ──► server/proxy socket            (raw bytes, pre-auth)
 TB2  Proxy ──► backend                           (forwarded identity)
 TB3  Client packets ──► Black Door state          (authenticated but adversarial)
 TB4  Staff commands ──► enforcement/config        (permissions)
 TB5  Black Door ──► storage / Redis               (credentials, queries)
 TB6  Black Door ──► webhooks / metrics endpoint   (outbound data, inbound scrapes)
 TB7  Black Door ──► blackdoor-edge ──► firewall    (privileged)
 TB8  Third-party plugins ──► Black Door API        (in-process)
 TB9  Build/release pipeline ──► distributed jar    (supply chain)
 TB10 Config files on disk ──► runtime             (admin-controlled, possibly edited by panel users)
```

## 4. STRIDE Analysis

### TB1 — Internet → socket
| STRIDE | Threat | Mitigation | Residual |
|---|---|---|---|
| S | Spoofed source IPs (SYN floods) | Out of scope for plugin; upstream/kernel (SYN cookies) | High without upstream protection |
| T | Malformed handshake/status/login frames | Packet state machine, size limits before decode | Low |
| D | Connection floods, ping floods, slow-loris logins, login floods with random names | Per-IP/prefix token buckets, global adaptive limits, handshake & login timeouts, bounded trackers, quarantine | Medium (TCP already accepted) |
| D | Oversized/compressed-bomb packets | Max frame size, compression ratio limit **[verify feasibility at decoder level]** | Medium |
| I | Server list info disclosure | Configurable ping response; not Black Door's core role | Low |

### TB2 — Proxy → backend
| STRIDE | Threat | Mitigation | Residual |
|---|---|---|---|
| S | Direct backend connection with forged forwarded UUID/IP (BungeeCord legacy forwarding) | Detect proxy mode; require Velocity modern forwarding or a token-based guard; warn loudly if backend reachable and legacy forwarding without guard; optional source-IP allowlist of proxies | Medium for legacy setups |
| T | Forged plugin messages claiming to be from proxy | Never trust plugin messaging for security state; use authenticated channel with HMAC + timestamps + nonces | Low |
| R | Proxy denies having sent a ban | Signed, logged cross-server events | Low |

### TB3 — Client packets → state
| STRIDE | Threat | Mitigation | Residual |
|---|---|---|---|
| T | Cheat clients manipulate movement/combat | Physics prediction, checks | Medium (detection limits) |
| D | Packet floods, expensive-to-process packets (e.g., huge NBT, deep component trees, many book pages), check-CPU exhaustion | Stage-ordered validation, per-type rate limits, structural limits before parse, per-check time budget, circuit breakers | Low/Medium |
| D | Memory growth via per-player structures (entity tracker, world view, transaction backlog) | Hard caps, overflow ⇒ uncertainty + disconnect | Low |
| E | Exploit packets that cause dupes or server crashes | Exploit guards, version-specific | Medium (unknown zero-days) |
| I | Timing side channels revealing hidden info (e.g., check behavior reveals vanished staff) | Do not leak vanish/hidden state via responses; review | Low |

### TB4 — Staff commands
| STRIDE | Threat | Mitigation | Residual |
|---|---|---|---|
| S | Non-staff invoking commands (console spoofing via other plugins) | Granular permissions; destructive commands require explicit permission nodes not granted by `*` wildcard conventions where platform allows **[verify per platform]** | Medium (permission plugins vary) |
| T | Staff tampering with violation history | Append-only audit log; deletions recorded | Low |
| R | Staff denies issuing a ban/release/exemption | Audit log with actor, time, reason | Low |
| E | Staff granting themselves permanent exemptions | Exemptions are time-bounded by default; permanent exemptions require config-file change + logged | Low |
| D | Reload spam | Reload rate limit + single-flight | Low |

### TB5 — Storage
| STRIDE | Threat | Mitigation |
|---|---|---|
| T/E | SQL injection via player names, reasons, evidence | Prepared statements only; no string-built SQL (static analysis rule) |
| I | Credential leak in logs/config dumps | Secrets redacted in logs, diagnostics and `/blackdoor diagnostics`; support env-var/file references for secrets |
| I | Unencrypted DB connections | TLS supported and recommended; warning if remote host without TLS |
| D | DB slowness stalls server | Async only, bounded queues, timeouts, circuit breaker |
| T | Unsafe deserialization of stored evidence | Evidence stored as versioned JSON/structured columns; no Java serialization anywhere |

### TB6 — Webhooks & metrics
| STRIDE | Threat | Mitigation |
|---|---|---|
| I | Metrics endpoint exposed publicly | Disabled by default; binds to `127.0.0.1` by default; optional bearer token; never exposes player IPs |
| S/I | SSRF via admin-configured webhook URL pointing to internal services | Webhook URLs allow only `https` by default; resolve and block private/link-local/loopback ranges unless explicitly allowed; no redirects followed |
| I | Sensitive data in webhooks (IPs, evidence) | Configurable fields; IPs hashed/omitted by default |
| D | Webhook endpoint slow | Async, bounded, timeouts, drop on saturation |

### TB7 — Edge agent / firewall
| STRIDE | Threat | Mitigation |
|---|---|---|
| E | Plugin compromise ⇒ arbitrary firewall/shell control | Agent exposes only `block(ip, ttl, reason)` / `unblock(ip)` / `list`; validates IP literals; TTL max cap; no shell — uses netlink/nft library or fixed argv without shell; runs with only `CAP_NET_ADMIN`; local Unix socket with file permissions + HMAC-authenticated requests |
| D | Attacker triggers mass blocks of legitimate players (e.g., spoofed reports, NAT) | Requires explicit admin enablement; global rate limit on new rules; max set size; never blocks allowlisted ranges/proxies; all rules expire |
| R | Unknown origin of rules | Audit log on both sides |

### TB8 — Third-party plugins (in-process)
| STRIDE | Threat | Mitigation |
|---|---|---|
| E | Plugin uses API to exempt cheaters broadly | API exemptions are scoped (check category, duration-capped), logged with calling plugin name; core packet/exploit guards are not exemptible via API |
| T | Plugin cancels Black Door events to hide violations | Violation events are informational; cancellation (where offered) only affects the action, and cancellation is logged |
| — | Malicious plugin | Same JVM: **cannot be defended against**. Documented limitation. |

### TB9 — Supply chain
| STRIDE | Threat | Mitigation |
|---|---|---|
| T | Compromised dependency or Gradle plugin | Dependency locking, Gradle checksum + signature verification, minimal deps, Renovate/Dependabot PRs reviewed by humans, osv-scanner in CI |
| T | Tampered release artifact | Reproducible builds where feasible; signed releases (Sigstore or GPG); published SHA-256; SBOM (CycloneDX) |
| S | Fake "update" mechanism | No self-updater. Update check (if ever) is opt-in, notify-only, HTTPS, pinned host, never downloads code |
| E | CI secret theft via PRs | No secrets in PR builds from forks; least-privilege tokens |

### TB10 — Config files
| STRIDE | Threat | Mitigation |
|---|---|---|
| T | Panel users with file access disable protections | Out of scope (file access = admin). Startup logs a config fingerprint and diff summary on change |
| D | Malicious/huge YAML (alias bombs) | YAML loader with alias/depth/size limits |
| — | Path traversal via config values (e.g., export paths) | Paths resolved and constrained to plugin data folder |

## 5. Abuse Cases Specific to Anti-Cheat

| Case | Mitigation |
|---|---|
| Cheater intentionally triggers FPs on others (e.g., pushing them into ghost blocks, knockback chains) | Uncertainty model; correlation requires multiple independent checks; no auto-punish on single heuristic |
| Attacker makes many players from one CGNAT IP look like a bot flood | Prefix limits are soft (throttle, not ban); quarantine is session-scoped; no permanent IP bans by default |
| Probing thresholds (cheat devs tune to stay below) | Expected; prediction-based checks leave little headroom; thresholds not exposed to players |
| Staff using alerts to harass | Alerts permission-gated; audit trail |

## 6. Top Risks (ranked)

1. Unknown exploit/crash vectors in new versions (mitigation: version onboarding, fuzzing).
2. False-positive mass punishment after a game update (mitigation: monitor defaults, unsupported-version gating, FP corpus).
3. Legacy forwarding identity spoofing on misconfigured networks (mitigation: loud startup checks).
4. Supply-chain compromise (mitigation: TB9 controls).
5. Hot-path CPU regression causing server-wide latency (mitigation: budgets, circuit breakers, benchmarks in CI).

## 7. Change Log

| Date | Change |
|---|---|
| 2026-11-26 | Initial Phase 0 draft. |
