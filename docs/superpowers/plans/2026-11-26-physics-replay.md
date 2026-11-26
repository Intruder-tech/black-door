# Trace Replay and Vertical Movement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replay the golden traces and predict the player's height with no misses.

**Architecture:** A pure module `blackdoor-physics` parses format-2 traces into ticks, keeps a small world of block-state ids, and predicts vertical movement against boxes from `BlockShapes`. It runs only in tests.

**Tech Stack:** Java 17 bytecode, Gradle, JUnit 5, AssertJ. No new third-party dependencies.

**Spec:** [docs/superpowers/specs/2026-11-26-physics-replay-design.md](../specs/2026-11-26-physics-replay-design.md)

## Global Constraints

- Gravity `dy' = (dy - 0.08) * 0.98`. Jump dy `0.42`, plus `0.1` per jump boost level (amplifier + 1).
- Player box 0.6 wide, 1.8 tall, 1.5 sneaking. Tolerance one constant `1e-4`.
- Pass: zero misses on traces 101, 102, 104; at least 80 % of airborne survival ticks of trace 102 get a verdict.
- Anything not modelled is **no verdict** with a reason, never a miss and never a silent match.
- Nothing runs on the server. Compiler uses `-Werror`. Branch `feat/physics-replay`. Commit only when asked.

## Review Focus

1. Water, ladder and cobweb have no collision box but must give no verdict, not "air" (Task 1).
2. A tick without a `move` means the position did not change: replay it as unchanged, do not skip it (Task 4).
3. A chunk never received, or unloaded: no verdict (Task 3).
4. A truncated or damaged trace line: fail loudly with the line number (Task 2).
5. The creative parts of traces 101 and 104 must be no verdict from the first tick, not from the first `flying` event (Task 4).

---

### Task 1: Special blocks in the shape table

The spec's "no verdict for fluid, climbable or special block" needs this: the table holds only boxes, so water looks like air.

**Files:** Modify `BlockShapes.java`, `ShapeTableBuilder.java`, `BlockShapesTest.java`, `ShapeTableBuilderTest.java`; regenerate `testdata/block-shapes/26.3.txt.gz`.

**Interfaces — Produces:** `void BlockShapes.putSpecial(int id, List<Box> boxes)`; `boolean special(int id)`. `boxes(id)` still returns the boxes. File: a special entry's value starts with `!` (`89 !-`).

Special: `water`, `lava`, `bubble_column`, `ladder`, `vine`, `weeping_vines`, `weeping_vines_plant`, `twisting_vines`, `twisting_vines_plant`, `cave_vines`, `cave_vines_plant`, `cobweb`, `slime_block`, `honey_block`, `sweet_berry_bush`, `kelp`, `kelp_plant`, `seagrass`, `tall_seagrass`, names ending `_bed`, and any state containing `waterlogged=true`.

- [ ] **Step 1:** Failing tests: `putSpecial(89, List.of())` gives an empty box list and `special(89)` true and survives write and read; the builder marks `minecraft:water[level=0]` and `minecraft:oak_slab[type=bottom,waterlogged=true]` special and `minecraft:stone` not.
- [ ] **Step 2:** `./gradlew :blackdoor-protocol:test :blackdoor-core:test` — Expected: compile failure.
- [ ] **Step 3:** Implement. **Step 4:** Same command passes.
- [ ] **Step 5:** Regenerate the table with `shapes.js` in the session scratchpad (temporary server, never the owner's). Expected: 35723 entries, water starts with `!`.

### Task 2: Module and trace reader

**Files:** Create `blackdoor-physics/build.gradle.kts` (java conventions, `api(project(":blackdoor-protocol"))`); add the module to `settings.gradle.kts` and as layer 3 in the root `build.gradle.kts`; create `Json.java` and `Trace.java` in `io.github.intruder_tech.blackdoor.physics.replay`; test `TraceTest.java`.

**Interfaces — Produces:** `record Trace(Map<String, Object> meta, List<Trace.Tick> ticks)`; `record Tick(List<Map<String, Object>> events, Map<String, Object> move)` where `move` is null when none; `static Trace Trace.read(Reader in)` (a `tick` line closes a tick); `static Object Json.parse(String)` giving `Map`, `List`, `String`, `Double`, `Boolean` or null.

- [ ] **Step 1:** Failing test: lines meta, move, tick, tick, velocity, move, tick give 3 ticks; the second has a null move; the third has one `velocity` event and a move; a broken second line throws `IllegalArgumentException` containing `line 2`.
- [ ] **Steps 2–4:** Fail, implement, pass: `./gradlew :blackdoor-physics:test`.

### Task 3: World and vertical collision

**Files:** Create `TraceWorld.java`, `VerticalCollision.java`; test `VerticalCollisionTest.java`.

**Interfaces — Produces:** `TraceWorld` with `void apply(Map<String, Object> event)` (`chunk`, `block`, `unload`, `world`) and `OptionalInt block(int x, int y, int z)` (empty = chunk unknown; a missing section is 0). `record Clip(double dy, boolean landed, boolean ceiling)`; `static Optional<Clip> VerticalCollision.clip(TraceWorld world, BlockShapes shapes, double x, double y, double z, double height, double dy)` — empty when any touched block is unknown, special, or in an unknown chunk. Boxes may reach above 1, so also examine the block layer below.

- [ ] **Step 1:** Failing tests with a hand-built table: falling 0.5 from 0.2 above stone gives dy -0.2, landed; rising into a ceiling clips and sets `ceiling`; a fence below (height 1.5) stops at 1.5; water or a missing chunk gives empty; a player straddling two blocks lands on the higher one.
- [ ] **Steps 2–4:** Fail, implement, pass.

### Task 4: Replay and verdicts

**Files:** Create `VerticalReplay.java`; tests `VerticalReplayTest.java`, `GoldenTraceTest.java`.

**Interfaces — Produces:** `record Result(int matches, int misses, Map<String, Integer> noVerdict, int airborne, int airborneJudged, List<String> firstMisses)`; `static Result VerticalReplay.run(Trace trace, BlockShapes shapes)`.

Per tick: apply events; candidates are continue `(dy-0.08)*0.98`, jump (previous move on ground and `input.jump`), clipped; match if the reported dy is within tolerance of one; accept the reported position. No-verdict reasons: `creative` (`gamemode` events), `flying`, `velocity` (until the next on-ground tick), `teleport` (until `confirm`, plus one tick), `effect:levitation`, `effect:slow_falling`, `block` (clip empty), `first` (no previous state), `step` (stayed on ground and rose at most 0.6).

- [ ] **Step 1:** Failing synthetic tests: a five-tick free fall matches; a jump from ground matches 0.42, and 0.62 with jump boost amplifier 1; a hover in the air is a **miss**; the same hover in creative is no verdict.
- [ ] **Step 2:** Golden test reading `testdata/block-shapes/26.3.txt.gz` and each trace: misses 0, and for trace 102 `airborneJudged >= 0.8 * airborne`; print the reasons.
- [ ] **Step 3:** Implement. For every golden miss use superpowers:systematic-debugging. Never widen the tolerance or add a no-verdict reason without writing why in the ledger.
- [ ] **Step 4:** `./gradlew build` passes.
- [ ] **Step 5:** Update `IMPLEMENTATION.md` (2.4b row with match, miss and no-verdict counts), `docs/PHYSICS_ENGINE.md` (verified 26.3 constants), `docs/ARCHITECTURE.md` if its module table differs.
