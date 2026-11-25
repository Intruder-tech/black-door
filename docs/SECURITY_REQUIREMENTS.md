# Security Requirements

> **Phase:** 0 · **Status:** Draft for review. Each requirement gets a test or review gate.
> Verification column: **T** = automated test, **R** = code-review checklist, **S** = static analysis.

| ID | Requirement | Verify |
|---|---|---|
| **Input handling** | | |
| SR-01 | Every inbound packet passes size and state validation before any field decoding beyond the packet ID. | T |
| SR-02 | All numeric fields from clients are checked for finiteness and bounds before use. | T, S |
| SR-03 | All strings from clients have length limits enforced before storage, logging or display. | T |
| SR-04 | Structured payloads (NBT, components, books) are validated with depth, size and count limits before full parsing where the library allows. | T (fuzz) |
| **Resource bounds** | | |
| SR-10 | Every queue, buffer, cache and map keyed by external input has a hard maximum size and a documented overflow policy. | R, T |
| SR-11 | No unbounded retries; all retries use capped exponential backoff. | R |
| SR-12 | Per-check CPU time budget with circuit breaker. | T |
| SR-13 | Per-player memory budget enforced and observable via `/blackdoor diagnostics`. | T |
| **Threading** | | |
| SR-20 | No blocking I/O on Netty event loops, the main thread or Folia region threads. | T (thread-assert harness), R |
| SR-21 | Platform world/entity APIs accessed only through `PlatformScheduler` on the owning thread. | T, R |
| **Storage** | | |
| SR-30 | SQL only through prepared statements; no string concatenation into SQL. | S |
| SR-31 | No Java native serialization anywhere (no `ObjectInputStream`). | S |
| SR-32 | Data minimization: IP addresses stored only when network protection requires; configurable hashing (keyed HMAC) for analytics; retention limits default-on. | R, T |
| **Secrets** | | |
| SR-40 | No secrets in source, tests or default configs. Secret scanning in CI. | S |
| SR-41 | Secrets accepted from config, environment variable or file reference; never logged; redacted in diagnostics. | T |
| **Network-facing features** | | |
| SR-50 | Metrics/admin HTTP endpoints disabled by default; bind loopback by default; require token when bound non-loopback. | T |
| SR-51 | Webhook targets: HTTPS only by default; private/loopback/link-local destinations blocked unless allowlisted; no redirects; timeouts. | T |
| SR-52 | No outbound connections at all unless a feature requiring them is enabled. | T (network-deny test) |
| **Proxy identity** | | |
| SR-60 | Backend refuses forwarded identity it cannot authenticate (Velocity modern forwarding secret, or configured token guard). | T |
| SR-61 | Startup check warns (and optionally refuses to start) when legacy forwarding is enabled without a guard. | T |
| **Authorization & audit** | | |
| SR-70 | Every command has its own permission node; destructive/sensitive commands have separate nodes. | T |
| SR-71 | All enforcement actions, exemptions, releases and config reloads are written to an append-only audit log with actor and reason. | T |
| SR-72 | No automatic permanent bans unless explicitly configured; configuration requires an explicit `allow-permanent-bans: true`. | T |
| **External enforcement** | | |
| SR-80 | The plugin never spawns processes or shells. | S (ban `ProcessBuilder`/`Runtime.exec`) |
| SR-81 | Edge agent accepts only typed, authenticated requests; all firewall entries have bounded TTL. | T |
| **Extensibility** | | |
| SR-90 | API cannot disable packet-security or exploit guards. | T |
| SR-91 | API exemptions are scoped, duration-capped and logged with the calling plugin. | T |
| **Supply chain & release** | | |
| SR-100 | Dependency locking, checksum verification, vulnerability scanning in CI; build fails on known critical CVEs in shipped deps. | CI |
| SR-101 | Release artifacts signed; SHA-256 published; SBOM attached. | CI |
| SR-102 | No self-update mechanism; no hidden telemetry; no remote command execution. | R, S |
| **Logging** | | |
| SR-110 | Logs rate-limited per category; player-controlled strings sanitized (control characters, log-injection). | T |
| SR-111 | Log levels never include secrets or full evidence at INFO. | R |
| **Configuration** | | |
| SR-120 | YAML parsing with alias/depth/size limits. | T |
| SR-121 | File paths from config constrained to the plugin data folder. | T |
| SR-122 | Invalid config on reload ⇒ keep previous config; never partially apply. | T |
