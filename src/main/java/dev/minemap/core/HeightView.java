package dev.minemap.core;

/** Pure height selection and bounded cave-column rendering, independent of Minecraft. */
public final class HeightView {
    private HeightView() { }
    public record Selection(boolean slice, int y, boolean manual) { }
    public static Selection select(int playerY, int surfaceY, boolean nether, Integer manualY, int minY, int maxY) {
        int y = Math.clamp(manualY == null ? playerY : manualY, minY, maxY);
        return new Selection(manualY != null || nether || playerY + 2 < surfaceY, y, manualY != null);
    }
    public interface Column {
        boolean solid(int y);
        int color(int y);
        boolean fluid(int y);
    }
    /** Dark walls; bright floors within 16 blocks below the selected plane. */
    public static int cave(Column column, int y, int minY, int maxY) {
        if (column.solid(y)) return shade(column.color(y), .28);
        if (y < maxY && column.solid(y + 1)) return shade(column.color(y + 1), .28);
        if (column.fluid(y)) return shade(column.color(y), .95);
        for (int floor = y - 1; floor >= Math.max(minY, y - 16); floor--) {
            if (column.solid(floor) || column.fluid(floor))
                return shade(column.color(floor), .95 - (y - 1 - floor) * .025);
        }
        return 0xff24343e; // Open shaft/void, distinct from unknown (transparent) chunks.
    }
    public static int shade(int rgb, double amount) {
        if (rgb == 0) rgb = 0x777777;
        int r = (int) (((rgb >> 16) & 255) * amount);
        int g = (int) (((rgb >> 8) & 255) * amount);
        int b = (int) ((rgb & 255) * amount);
        return 0xff000000 | r << 16 | g << 8 | b;
    }
}
