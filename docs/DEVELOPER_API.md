# Developer API

> **Phase:** 0 · **Status:** Design outline. API v1 ships in Phase 6. Names are illustrative.

## 1. Principles

- `blackdoor-api` has no third-party dependencies and follows semantic versioning.
- Read-only snapshots for state; no mutable internals exposed.
- Extensions cannot disable packet-security or exploit guards (SR-90).
- All extension effects (exemptions, custom actions) are attributed to the calling plugin and
  audited.

## 2. Surfaces

| Surface | Purpose |
|---|---|
| `BlackDoor.get()` / service registry | Entry point (platform-specific registration) |
| `PlayerSecurityView` | Snapshot: client version, latency, active uncertainty flags, quarantine status, violation summary |
| `CheckRegistry` + `Check` SPI | Register custom checks with the full contract (DETECTION_REGISTRY.md §1); runs inside the same budgets/circuit breakers |
| `ExemptionService` | Scoped, duration-capped exemptions (e.g., a launch-pad plugin exempts `movement.*` for 3 s) |
| Events | `ViolationEvent`, `EnforcementEvent` (cancellable action only; cancellation logged), `SecurityEvent` (exploit/packet guard), `NetworkEvent` (throttle, attack-mode change) |
| `MovementHint` | Let plugins declare upcoming velocity/teleports so physics treats them as expected instead of exempting checks |

`MovementHint` is the preferred integration for movement plugins: it keeps checks running with
correct expectations instead of creating blind spots.

## 3. Compatibility Policy

- Minor versions add; never remove or change semantics.
- Deprecations last at least one major version.
- API compatibility checked in CI (binary compatibility tool, e.g., japicmp).
