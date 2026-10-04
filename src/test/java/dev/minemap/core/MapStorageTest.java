package dev.minemap.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapStorageTest {
    @TempDir Path root;
    @Test void savedChunksSurviveRestartAndPartialRefreshDoesNotEraseHistory() throws Exception {
        Path file = root.resolve("world/surface.map");
        int[] old = new int[256], changed = new int[256]; Arrays.fill(old, 1); Arrays.fill(changed, 2);
        try (var store = new ChunkMapStore()) { store.save(file, Map.of(ChunkMapStore.key(-2, 7), old)).join(); }
        try (var store = new ChunkMapStore()) {
            assertArrayEquals(old, store.load(file).join().get(ChunkMapStore.key(-2, 7)));
            store.save(file, Map.of(ChunkMapStore.key(8, -3), changed)).join();
            var loaded = store.load(file).join(); assertEquals(2, loaded.size());
            assertArrayEquals(old, loaded.get(ChunkMapStore.key(-2, 7)));
            store.save(file, Map.of(ChunkMapStore.key(-2, 7), changed)).join();
            assertArrayEquals(changed, store.load(file).join().get(ChunkMapStore.key(-2, 7)));
            assertTrue(store.load(root.resolve("world/y-32.map")).join().isEmpty());
        }
    }
    @Test void markersAreNamedColoredPersistentAndSeparatedByWorld() throws Exception {
        var store = new PointStore(root);
        var point = new MapPoint("id", "Пещера \"дом\"", "#64b5ff", -12, 20, 35);
        store.put("world-a", point);
        var reopened = new PointStore(root);
        assertEquals(List.of(point), reopened.get("world-a")); assertTrue(reopened.get("world-b").isEmpty());
        assertTrue(point.json().contains("\\\"дом\\\""));
        reopened.delete("world-a", "id"); assertTrue(new PointStore(root).get("world-a").isEmpty());
    }
    private MapFrame frame(List<MapTile> tiles) {
        return new MapFrame(true, 1, "overworld", 0, 0, 0, 0, 0, 16, 202, "", 20, 20, -64, 319, true, false, "y20", 1, 10, tiles, "world");
    }
    @Test void chunkDeltasArePagedAndUnchangedChunksAreOmitted() {
        var tiles = new ArrayList<MapTile>(); for(int i=0;i<200;i++) tiles.add(new MapTile(i, 0, i+2, "png"));
        String first = frame(tiles).json(-1);
        assertTrue(first.contains("\"revision\":129"));
        assertFalse(first.contains("\"x\":128,\"z\":0"));
        String second = frame(tiles).json(129);
        assertTrue(second.contains("\"x\":128,\"z\":0")); assertTrue(second.contains("\"revision\":202"));
        assertTrue(frame(tiles).json(202).contains("\"tiles\":[]"));
    }
}
