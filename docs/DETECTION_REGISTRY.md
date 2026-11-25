# Detection Registry

> **Phase:** 0 · **Status:** Planned (no check is implemented) · **Last updated:** 2026-11-26
> **No detection accuracy figures exist.** Accuracy and FP rates will only be published from the
> evaluation corpus described in TESTING_STRATEGY.md §6.

## 1. Check Contract

Every check must declare all fields below in code (annotation/descriptor) and in this registry.
A check without all fields cannot be registered (enforced at build time).

| Field | Description |
|---|---|
| `id` | Stable identifier, `category.name` (e.g., `movement.fly`). Never reused. |
| `category` | combat · movement · world · inventory · automation · packet · info · exploit |
| `versions` | Client/server version range via feature flags. |
| `requires` | State needed (e.g., `world-view`, `entity-tracker`, `transactions`). |
| `confidence` | Class: **Deterministic** (impossible under protocol/physics), **High** (prediction-based, small residual uncertainty), **Heuristic** (pattern-based), **Statistical** (probabilistic, review only). |
| `weight` | Violation points per finding (scaled by confidence). |
| `decay` | Half-life of violation points. |
| `exemptions` | Conditions that suppress the check (see FALSE_POSITIVE_POLICY.md). |
| `cost` | Expected per-invocation cost class: O(1), O(sim), O(entities), async. |
| `fp_notes` | Known false-positive sources. |
| `actions` | Allowed actions: monitor, alert, drop, cancel, setback, restrict, kick, tempban. (Permanent ban never default.) |
| `tests` | Required test types: unit, golden-legit, cheat-replay, version-matrix. |
| `phase` | Implementation phase. |

**Auto-enforcement eligibility:** only Deterministic and High checks may ever be configured for
automatic kick/tempban. Heuristic checks max out at setback/alert. Statistical checks are
**alert/review only** and cannot be configured to punish on their own (enforced by the policy
engine, not just by defaults).

## 2. Movement

| ID | Method | Confidence | Requires | Key FP sources | Phase |
|---|---|---|---|---|---|
| `movement.prediction` | Physics offset vs. valid-movement set (PHYSICS_ENGINE.md) — umbrella check whose sub-findings classify into Fly/Speed/etc. | High | world-view, transactions | Unknown blocks, plugin velocity, new versions | 2–3 |
| `movement.fly` | Sustained vertical offset where gravity should apply | High | prediction | Levitation, slow falling, plugin flight, elytra | 3 |
| `movement.speed` | Horizontal offset beyond best candidate | High | prediction | Speed effects, attributes, ice, knockback | 3 |
| `movement.bhop` | Jump-sprint horizontal gain inconsistent with model | High | prediction | Ice + trapdoor tricks (legit), attributes | 3 |
| `movement.step` | Vertical step > step-height attribute without jump | High/Deterministic | world-view | Step-height attribute changes (modern) | 3 |
| `movement.spider` | Vertical climb without climbable block | High | world-view | Scaffolding, vines, ghost blocks | 3 |
| `movement.jesus` | Standing/walking on fluid surface | High | world-view | Frost walker, lily pads, boats | 3 |
| `movement.nofall` | Ground flag true while prediction says airborne; fall distance reset | High | prediction | Ghost blocks, server-side ground mismatch | 3 |
| `movement.glide` | Descent slower than gravity without slow falling/elytra | High | prediction | Slow falling, cobweb, honey | 3 |
| `movement.airjump` | Jump impulse while not on ground | High | prediction | Ghost blocks, latency on block break | 3 |
| `movement.highjump` | Jump apex above jump-strength model | High | prediction | Jump boost, attribute, slime | 3 |
| `movement.timer` | Movement packets per real time above tick rate (balance with transaction anchoring; idle-packet aware) | High | transactions | Lag spikes then bursts (must allow catch-up with bounded balance); server lag | 2 |
| `movement.fastladder` | Climb speed > model | High | prediction | Version climb speed differences | 3 |
| `movement.noslow` | Using item / sneaking / in web without slowdown | High | prediction, item-use state | Version-specific item-use slowdown; desync of use state | 3 |
| `movement.phase` | Movement through solid collision | Deterministic | world-view | Unknown blocks, block changes not yet acked | 2 |
| `movement.strafe` | Air acceleration direction impossible under inputs | High | prediction | Knockback | 3 |
| `movement.ground-spoof` | Ground flag inconsistent with collision | Deterministic/High | world-view | Edge cases at block edges, boats | 2 |
| `movement.vehicle` | Vehicle speed/teleport sanity; impossible vehicle moves | Heuristic → High later | vehicle state | Many vehicle mechanics | post-3 |
| `movement.invalid-position` | NaN/Inf/out-of-border/huge deltas | Deterministic | — | None legitimate | 2 |

## 3. Combat

| ID | Method | Confidence | Requires | Key FP sources | Phase |
|---|---|---|---|---|---|
| `combat.reach` | Distance from latency-compensated eye position to compensated target hitbox (all possible interpolation positions) > reach + ε | High | entity-tracker, transactions | Interpolation differences, entity scale attribute, Via | 3 |
| `combat.hitbox` | Ray from reported rotation misses all possible target boxes | High | entity-tracker | Rotation precision per version; fast-moving targets | 3 |
| `combat.multiaura` | Multiple distinct targets attacked in one tick beyond protocol possibility | Deterministic | — | Sweep attacks are server-side, not client packets | 3 |
| `combat.killaura` | Composite: attacks without line of sight, attack-while-inventory-open, rotation snaps aligned with attacks, attacks through blocks | Heuristic (composite) | entity-tracker, world-view | Laggy targets, wall-adjacent fights | 3 |
| `combat.aimassist` | Rotation statistics (GCD/sensitivity consistency, jerk, correlation with target motion) | Statistical | rotation history | Mouse hardware, controllers, sensitivity changes, Bedrock | 5 |
| `combat.triggerbot` | Attack reaction time distribution when crosshair enters hitbox | Statistical | entity-tracker | Skilled players | 5 |
| `combat.autoclicker` | Click interval distribution (variance, kurtosis, outliers) | Statistical | swing/attack timing | Butterfly/jitter clicking, hardware macros allowed by servers | 5 |
| `combat.criticals` | Crit claimed (falling, not on ground) inconsistent with prediction | High | prediction | Ghost blocks | 3 |
| `combat.antikb` | Velocity acknowledged (transaction) but movement ignores it | High | transactions, prediction | Knockback resistance, blocks, plugin velocity cancellation | 3 |
| `combat.velocity` | Partial velocity application (reduced KB) | High | prediction | Same | 3 |
| `combat.rotation-invalid` | Pitch out of range, impossible rotation deltas per version | Deterministic | — | None | 2 |
| `combat.attack-timing` | Attack before arm swing (where version requires order), attack while dead/in bed/with closed state | Deterministic (per version) | packet order | Version ordering differences (1.8 vs 1.9+) [verify] | 3 |
| `combat.invalid-interaction` | Interact with unknown/untracked/self/too-far entity, dead entity spam | Deterministic | entity-tracker | NPC plugins (fake entities) — must recognize client-known IDs | 2 |

## 4. World Interaction

| ID | Method | Confidence | Phase |
|---|---|---|---|
| `world.reach-block` | Interaction distance to block > limit (attribute-aware) | High | 3 |
| `world.placement-geometry` | Clicked face not visible / cursor position outside face / against air | Deterministic/High | 3 |
| `world.scaffold` | Composite: placement geometry + rotation consistency + sprint-bridge physics | Heuristic | 3 |
| `world.tower` | Vertical placement rate vs. jump physics | High | 3 |
| `world.fastplace` | Placement rate above client cooldown (per version) | High | 3 |
| `world.fastbreak` | Break completion earlier than per-version break-time formula (tool, enchant, effects, on-ground, in-water) with latency tolerance | High | 3 |
| `world.nuker` | Multiple blocks broken in one tick / out of view | Deterministic/High | 3 |
| `world.ghosthand` | Interaction through solid blocks (no line of sight to face) | High | 3 |
| `world.invalid-dig-sequence` | Finish without start, start/abort spam, dig at distance | Deterministic | 2 |

## 5. Inventory

| ID | Method | Confidence | Phase |
|---|---|---|---|
| `inventory.invalid-click` | Slot/button/mode combinations not producible by client; stale state IDs (1.17.1+) | Deterministic | 2 |
| `inventory.move` | Clicks while prediction says player is moving with inputs (≥ 1.21.2 input packet makes this practical; older versions heuristic) | High (≥ input packet) / Heuristic | 3 |
| `inventory.autoarmor` | Armor equip timing/sequence impossible for human (sub-tick multi-slot) | Heuristic | 3 |
| `inventory.autototem` | Totem to offhand within impossible time after pop, while inventory closed | Heuristic | 3 |
| `inventory.cheststealer` | Container click rate/order vs. human limits | Heuristic | 3 |
| `inventory.fastuse` | Item use completion before use duration (eating, bow charge) | High | 3 |
| `inventory.autoeat` | Eat-on-threshold pattern | Statistical | 5 |
| `inventory.impossible-transaction` | Server-authoritative mismatch (client claims item it doesn't have) | Deterministic | 2 |

## 6. Packet-Based

| ID | Method | Confidence | Phase |
|---|---|---|---|
| `packet.badpackets` | Family of deterministic protocol violations (out-of-order, wrong state, impossible field values, duplicate sequence numbers) | Deterministic | 2 |
| `packet.blink` | Movement silence while transactions continue / bursts of buffered movement exceeding catch-up budget | High | 2 |
| `packet.packetfly` | Teleport-confirm abuse; position packets designed to trigger server teleports | Deterministic/High | 2 |
| `packet.timer` | Alias of `movement.timer` for packet-cadence view | High | 2 |
| `packet.transaction` | Missing, reordered or forged transaction responses | Deterministic | 2 |
| `packet.flood` | Per-type rate exceeding budgets (packets module) | Deterministic | 2 |
| `packet.invalid-rotation` | Pitch ∉ [-90, 90], non-finite yaw | Deterministic | 2 |

## 7. Automation (all Statistical — never auto-punish)

| ID | Signal | Phase |
|---|---|---|
| `automation.autofish` | Reel timing relative to bite packet; repetition without rotation | 5 |
| `automation.autofarm` | Path/timing regularity during farming | 5 |
| `automation.automine` | Mining paths, rotation regularity, AFK + action correlation | 5 |
| `automation.autotool` | Hotbar switch to optimal tool on same tick as dig start, consistently | 5 |
| `automation.macro` | Repetitive identical action sequences (sequence entropy) | 5 |
| `automation.bot-account` | Session-level: login patterns, identical behaviour across accounts | 5 |

## 8. Information Cheats

| Threat | Prevention (preferred) | Detection | Status |
|---|---|---|---|
| X-Ray | Integrate Paper anti-xray / Orebfuscator; validate config at startup and recommend settings | `info.xray-stats`: ore-to-stone ratio, direct tunnelling to hidden ores (Statistical, review only) | Planned (integration P4, stats P5) |
| ESP / Tracers | Optional entity visibility culling (don't send entities without plausible line of sight) — performance-heavy, experimental (OD-8) | Aim/rotation correlation with hidden entities (Statistical) | Experimental, later |
| ChestESP | Hide container block entities until plausible visibility (experimental) | None reliable | Experimental, later |
| Freecam | — | Interactions/attacks inconsistent with actual camera position are covered by reach/LoS checks; pure viewing produces **no evidence** | Detection **unsupported** for pure viewing |

Black Door does **not** claim to detect purely client-side visual modifications that produce no
observable server-side evidence.

## 9. Check Lifecycle

`draft → experimental (monitor-only, disabled by default) → validated (FP corpus pass, staff
review on test network) → enforcement-eligible`. Promotion requires recorded evidence in
SECURITY_AUDIT.md / BENCHMARK_REPORT.md. Demotion is automatic if the FP corpus regresses.
