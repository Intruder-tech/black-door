package io.github.intruder_tech.blackdoor.world;

import io.github.intruder_tech.blackdoor.protocol.world.BlockSource;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeClass;
import io.github.intruder_tech.blackdoor.protocol.world.ShapeRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldTest {

    /** ids: 0 air, 1 stone, 2 water, 3 slab. Ground (stone) for y < 4 in section 0, water at (5,4,5). */
    static final ShapeRegistry REGISTRY = id -> switch (id) {
        case 1 -> ShapeClass.FULL;
        case 2 -> ShapeClass.FLUID;
        case 3 -> ShapeClass.PARTIAL;
        default -> ShapeClass.EMPTY;
    };

    static final BlockSource TERRAIN = new BlockSource() {
        @Override
        public int sections() {
            return 3;
        }

        @Override
        public boolean emptySection(int section) {
            return section == 2;
        }

        @Override
        public int blockId(int section, int x, int y, int z) {
            if (section == 1) {
                return 1;
            }
            if (y < 4) {
                return 1;
            }
            return x == 5 && y == 4 && z == 5 ? 2 : 0;
        }
    };

    @Test
    void sectionsAreCompactAndAccurate() {
        SectionShapes mixed = SectionShapes.of(TERRAIN, 0, REGISTRY);
        assertThat(mixed.isUniform()).isFalse();
        assertThat(mixed.at(0, 0, 0)).isEqualTo(ShapeClass.FULL);
        assertThat(mixed.at(5, 4, 5)).isEqualTo(ShapeClass.FLUID);
        assertThat(mixed.at(15, 15, 15)).isEqualTo(ShapeClass.EMPTY);
        assertThat(mixed.bytes()).isLessThan(1200);
        assertThat(SectionShapes.of(TERRAIN, 1, REGISTRY)).isSameAs(SectionShapes.uniform(ShapeClass.FULL));
        assertThat(SectionShapes.of(TERRAIN, 2, REGISTRY)).isSameAs(SectionShapes.uniform(ShapeClass.EMPTY));
    }

    @Test
    void copyOnWriteChangesOneBlockOnly() {
        SectionShapes s = SectionShapes.of(TERRAIN, 0, REGISTRY);
        SectionShapes changed = s.with(1, 1, 1, ShapeClass.EMPTY);
        assertThat(changed).isNotSameAs(s);
        assertThat(s.at(1, 1, 1)).isEqualTo(ShapeClass.FULL);
        assertThat(changed.at(1, 1, 1)).isEqualTo(ShapeClass.EMPTY);
        assertThat(changed.at(2, 1, 1)).isEqualTo(ShapeClass.FULL);
        assertThat(s.with(0, 0, 0, ShapeClass.FULL)).isSameAs(s);
    }

    @Test
    void cacheIsSharedReferenceCountedAndConvertsOnce() {
        WorldCache cache = new WorldCache(1 << 20);
        PlayerWorldView a = new PlayerWorldView(cache, "p:a");
        PlayerWorldView b = new PlayerWorldView(cache, "p:b");
        a.changeWorld("world");
        b.changeWorld("world");
        a.chunk(0, 0, -4, TERRAIN, REGISTRY);
        b.chunk(0, 0, -4, TERRAIN, REGISTRY);
        assertThat(cache.stats().chunks()).isEqualTo(1);
        assertThat(cache.stats().conversions()).isEqualTo(1);
        assertThat(a.shapeAt(0, -64, 0)).isEqualTo(ShapeClass.FULL);
        assertThat(b.shapeAt(5, -60, 5)).isEqualTo(ShapeClass.FLUID);
        assertThat(a.shapeAt(100, 0, 100)).as("not loaded").isNull();
        a.unload(0, 0);
        assertThat(cache.stats().chunks()).isEqualTo(1);
        assertThat(a.shapeAt(0, -64, 0)).isNull();
        b.releaseAll();
        assertThat(cache.stats().chunks()).isZero();
        assertThat(cache.stats().usedBytes()).isZero();
    }

    @Test
    void worldsWithoutNamesStayPrivate() {
        WorldCache cache = new WorldCache(1 << 20);
        PlayerWorldView a = new PlayerWorldView(cache, "p:a");
        PlayerWorldView b = new PlayerWorldView(cache, "p:b");
        a.changeWorld(null);
        b.changeWorld(null);
        a.chunk(0, 0, -4, TERRAIN, REGISTRY);
        b.chunk(0, 0, -4, TERRAIN, REGISTRY);
        assertThat(cache.stats().chunks()).isEqualTo(2);
    }

    @Test
    void worldChangeReleasesEverything() {
        WorldCache cache = new WorldCache(1 << 20);
        PlayerWorldView a = new PlayerWorldView(cache, "p:a");
        a.changeWorld("world");
        a.chunk(0, 0, -4, TERRAIN, REGISTRY);
        a.chunk(1, 0, -4, TERRAIN, REGISTRY);
        a.changeWorld("world_nether");
        assertThat(cache.stats().chunks()).isZero();
        assertThat(a.loadedChunks()).isZero();
    }

    @Test
    void blockChangesAreUncertainUntilAcknowledged() {
        WorldCache cache = new WorldCache(1 << 20);
        PlayerWorldView a = new PlayerWorldView(cache, "p:a");
        a.changeWorld("world");
        a.chunk(0, 0, -4, TERRAIN, REGISTRY);
        a.blockChange(1, -63, 1, ShapeClass.EMPTY, 7);
        assertThat(a.shapeAt(1, -63, 1)).isEqualTo(ShapeClass.EMPTY);
        assertThat(a.uncertainNear(1, -62, 1, 2)).isTrue();
        assertThat(a.uncertainNear(10, -62, 10, 2)).isFalse();
        a.acknowledged(6);
        assertThat(a.uncertainNear(1, -62, 1, 2)).isTrue();
        a.acknowledged(7);
        assertThat(a.uncertainNear(1, -62, 1, 2)).isFalse();
    }

    @Test
    void budgetRefusesInsteadOfGrowing() {
        WorldCache cache = new WorldCache(100);
        PlayerWorldView a = new PlayerWorldView(cache, "p:a");
        a.changeWorld("world");
        a.chunk(0, 0, -4, TERRAIN, REGISTRY);
        assertThat(cache.stats().refused()).isEqualTo(1);
        assertThat(a.loadedChunks()).isZero();
        assertThat(a.shapeAt(0, -64, 0)).isNull();
    }
}
