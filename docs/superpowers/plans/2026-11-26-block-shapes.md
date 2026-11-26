# Block Collision Shapes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A table from block-state id to exact collision boxes, built from the running server.

**Architecture:** `blackdoor-protocol` holds the table and its file format. The PacketEvents bridge names a state id. The Bukkit adapter asks the server for each name's boxes, a batch per tick, by reflection (it compiles against the 1.8.8 API).

**Tech Stack:** Java 17 bytecode, Gradle multi-module, JUnit 5, AssertJ.

**Spec:** [docs/superpowers/specs/2026-11-26-block-shapes-design.md](../specs/2026-11-26-block-shapes-design.md)

## Global Constraints

- Unknown means no verdict. Nothing in this step flags or blocks a player.
- Only the server thread calls server APIs. Packet threads only read.
- Only `blackdoor-protocol-packetevents` imports PacketEvents. Compiler runs with `-Werror`.
- Always unknown: `scaffolding`, `powder_snow`, `bamboo`, `pointed_dripstone`, `moving_piston`, and names ending `shulker_box`.
- Work on branch `feat/physics-core`. Commit only when the owner asks. Never touch the owner's test server.
- Build: `export JAVA_HOME="$HOME/.jdks/jdk-21.0.13.1+2"`, then `./gradlew build`.

## Review Focus

1. A packet thread asks for an id before the table reaches it, or an id out of range: unknown, no exception (Task 1).
2. A server without `getCollisionShape` (1.8.8, Spigot): one log line, no retries every tick (Task 3).
3. A state the server cannot parse: that id is unknown and building continues (Task 2).
4. Plugin disable or reload while building: the batch task stops (Task 2).
5. An empty box list (air) must stay distinct from unknown, also through the file (Task 1).

---

### Task 1: Table and file format

**Files:**
- Create: `blackdoor-protocol/src/main/java/io/github/intruder_tech/blackdoor/protocol/world/BlockShapes.java`
- Test: `blackdoor-protocol/src/test/java/io/github/intruder_tech/blackdoor/protocol/world/BlockShapesTest.java`

**Interfaces — Produces:** `BlockShapes` with `record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ)`; `static final int CAPACITY = 1 << 17`; `void put(int id, List<Box> boxes)`; `void putUnknown(int id)`; `Optional<List<Box>> boxes(int id)` (empty Optional = unknown, empty list = no collision); `int known()`; `void write(Writer out, String server)`; `static BlockShapes read(Reader in)`. Thread-safe (one writer, many readers; `AtomicReferenceArray`).

File format: first line `server <version>`, then `<id> -` (no collision), `<id> ?` (unknown) or `<id> x1,y1,z1,x2,y2,z2;...`.

- [ ] **Step 1: Failing tests**

```java
@Test
void unsetUnknownAndEmptyAreDifferent() {
    BlockShapes shapes = new BlockShapes();
    shapes.put(0, List.of());
    shapes.putUnknown(2);
    assertThat(shapes.boxes(0)).contains(List.of());
    for (int id : new int[]{1, 2, -1, BlockShapes.CAPACITY}) {
        assertThat(shapes.boxes(id)).isEmpty();
    }
    assertThat(shapes.known()).isEqualTo(1);
}

@Test
void roundTripsThroughTheFileFormat() throws Exception {
    BlockShapes shapes = new BlockShapes();
    var slab = new BlockShapes.Box(0, 0, 0, 1, 0.5, 1);
    var post = new BlockShapes.Box(0.375, 0, 0.375, 0.625, 1.5, 0.625);
    shapes.put(0, List.of());
    shapes.put(5, List.of(slab, post));
    shapes.putUnknown(9);
    StringWriter out = new StringWriter();
    shapes.write(out, "26.3");
    assertThat(out.toString()).startsWith("server 26.3\n").contains("0 -\n").contains("9 ?\n");
    BlockShapes back = BlockShapes.read(new StringReader(out.toString()));
    assertThat(back.boxes(5)).contains(List.of(slab, post));
    assertThat(back.boxes(0)).contains(List.of());
    assertThat(back.boxes(9)).isEmpty();
}
```

- [ ] **Step 2:** `./gradlew :blackdoor-protocol:test` — Expected: compile failure.
- [ ] **Step 3:** Implement. `put` outside the range is ignored.
- [ ] **Step 4:** Same command — Expected: pass.

### Task 2: Builder, bridge naming, diagnostics and dump

**Files:**
- Modify: `blackdoor-protocol/.../protocol/ProtocolBridge.java`, `blackdoor-protocol-packetevents/.../PacketEventsBridge.java`
- Create: `blackdoor-core/.../core/world/ShapeTableBuilder.java`, `core/command/sub/ShapesCommand.java`
- Modify: `blackdoor-core/.../core/BlackDoorEngine.java`, `core/command/sub/DiagnosticsCommand.java`, the platform interface in `core/platform`
- Test: `blackdoor-core/src/test/java/io/github/intruder_tech/blackdoor/core/world/ShapeTableBuilderTest.java`

**Interfaces — Produces:**
- `default Optional<String> ProtocolBridge.blockState(int id)` — empty when there is no such state. PacketEvents: `WrappedBlockState.getByGlobalId(id)`; empty when the result's `getGlobalId() != id`.
- `ShapeTableBuilder(IntFunction<Optional<String>> names, Function<String, Optional<List<BlockShapes.Box>>> server, BlockShapes into, int perBatch)` with `boolean step()` (true while more remain). Ends after 64 ids in a row without a name, or at `CAPACITY`. A name on the unknown list, an empty answer or a `RuntimeException` gives `putUnknown`.
- Platform: `default Function<String, Optional<List<BlockShapes.Box>>> blockShapeSource()` returning null when the server cannot answer.
- Engine: `Optional<BlockShapes> blockShapes()`; runs `step()` through `platform.scheduler().runGlobal`, re-submitting itself, 500 states per batch, stopping when disabled; logs the count and the longest batch in ms when done.
- Diagnostics row `Block shapes`: `ready <n>` / `building <n>` / `unavailable`. Command `shapes dump` (permission `blackdoor.command.record`) writes `shapes-<version>.txt.gz` in the data folder off-thread; refused while building.

- [ ] **Step 1: Failing test**

```java
@Test
void buildsInBatchesAndMarksWhatItCannotKnow() {
    List<String> states = List.of("minecraft:air", "minecraft:stone", "minecraft:scaffolding[bottom=false]",
            "minecraft:broken", "minecraft:red_shulker_box[facing=up]");
    var full = new BlockShapes.Box(0, 0, 0, 1, 1, 1);
    BlockShapes into = new BlockShapes();
    ShapeTableBuilder builder = new ShapeTableBuilder(
            id -> id < states.size() ? Optional.of(states.get(id)) : Optional.empty(),
            name -> {
                if (name.contains("broken")) {
                    throw new IllegalArgumentException(name);
                }
                return Optional.of(name.equals("minecraft:air") ? List.of() : List.of(full));
            }, into, 2);
    int steps = 1;
    while (builder.step()) {
        steps++;
    }
    assertThat(steps).isGreaterThan(2);
    assertThat(into.boxes(0)).contains(List.of());
    assertThat(into.boxes(1)).contains(List.of(full));
    assertThat(into.boxes(2)).isEmpty();
    assertThat(into.boxes(3)).isEmpty();
    assertThat(into.boxes(4)).isEmpty();
}
```

- [ ] **Step 2:** `./gradlew :blackdoor-core:test` — Expected: compile failure.
- [ ] **Step 3:** Implement. A null source means no builder, one info log, row `unavailable`.
- [ ] **Step 4:** Same command — Expected: pass.

### Task 3: Bukkit source

**Files:**
- Create: `blackdoor-platform-bukkit/.../platform/bukkit/BukkitBlockShapes.java`
- Modify: `BukkitPlatform.java`

**Interfaces — Consumes:** the platform's `blockShapeSource()`. By reflection: `Bukkit.createBlockData(String)`, `BlockData.getCollisionShape(Location)`, `VoxelShape.getBoundingBoxes()`, `BoundingBox.getMinX()` … `getMaxZ()`. Location: block 0,0,0 of the first world. Missing method or no world: the source is null.

Not unit-testable without a server; Task 4 verifies it.

- [ ] **Step 1:** Implement. **Step 2:** `./gradlew build` — Expected: BUILD SUCCESSFUL.

### Task 4: Live check, test table, documents

- [ ] **Step 1:** Copy the 26.3 test server's jar, libraries and plugins (PacketEvents only, plus the new build) into a **new** scratch directory with its own world, bound to 127.0.0.1 on another port, `online-mode=false`.
- [ ] **Step 2:** Start, wait for the ready log, run `blackdoor shapes dump`. Expected: no exceptions; a count above 20000; the longest batch under 50 ms (lower the batch size if not).
- [ ] **Step 3:** Check by name in the dump: air `-`; stone one box to 1,1,1; bottom oak slab to height 0.5; a stair two or more boxes; oak fence reaching 1.5.
- [ ] **Step 4:** Save the dump as `testdata/block-shapes/26.3.txt.gz`. Script: every block id under or at the feet in the three traces is known or on the unknown list. Report any other.
- [ ] **Step 5:** Update `IMPLEMENTATION.md` (2.4b row), `docs/KNOWN_LIMITATIONS.md` (spec's Known limits), `testdata/golden-traces/README.md` (traces 101, 102, 104).
