package dev.minemap.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HeightViewTest {
    @Test void automaticSurfaceCaveAndNether() {
        assertFalse(HeightView.select(100, 70, false, null, -64, 319).slice());
        assertFalse(HeightView.select(70, 70, false, null, -64, 319).slice());
        assertTrue(HeightView.select(-30, 70, false, null, -64, 319).slice());
        assertTrue(HeightView.select(110, 70, true, null, 0, 255).slice());
        var manual = HeightView.select(100, 70, false, -200, -64, 319);
        assertTrue(manual.slice()); assertTrue(manual.manual()); assertEquals(-64, manual.y());
    }
    private static HeightView.Column column(int wall, int floor, int water) {
        return new HeightView.Column() {
            public boolean solid(int y) { return y == wall || y == floor; }
            public boolean fluid(int y) { return y == water; }
            public int color(int y) { return y == water ? 0x2040ff : 0xaaaaaa; }
        };
    }
    @Test void floorIsBrighterThanWallAndOverheadSolidShowsWall() {
        int passage = HeightView.cave(column(999, 19, 999), 20, -64, 319);
        int wall = HeightView.cave(column(20, 19, 999), 20, -64, 319);
        int ceiling = HeightView.cave(column(21, 19, 999), 20, -64, 319);
        assertTrue((passage & 255) > (wall & 255)); assertEquals(wall, ceiling);
        int water = HeightView.cave(column(999, 19, 20), 20, -64, 319);
        assertTrue((water & 255) > ((water >> 16) & 255));
    }
    @Test void caveNeverReadsOutsideHeightBoundsOrScansEntireWorld() {
        var column = new HeightView.Column() {
            public boolean solid(int y) { assertTrue(y >= -64 && y <= 319); return false; }
            public boolean fluid(int y) { assertTrue(y >= -64 && y <= 319); return false; }
            public int color(int y) { return 0; }
        };
        assertEquals(0xff24343e, HeightView.cave(column, -64, -64, 319));
        assertEquals(0xff24343e, HeightView.cave(column, 319, -64, 319));
    }
}
