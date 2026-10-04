package dev.minemap.core;

import java.util.List;

/** Immutable frame published by the game thread and read by HTTP workers. */
public record MapFrame(boolean active, long worldId, String dimension, double x, double z, float yaw,
                       int originX, int originZ, int size, long revision, String pngBase64, int playerY, int mapY, int minY, int maxY,
                       boolean slice, boolean manualHeight, String viewId, long viewRevision, int radius, List<MapTile> tiles, String mapKey) {
    public MapFrame(boolean active, long worldId, String dimension, double x, double z, float yaw,
                    int originX, int originZ, int size, long revision, String pngBase64) {
        this(active, worldId, dimension, x, z, yaw, originX, originZ, size, revision, pngBase64, 64, 64, -64, 319, false, false, "surface", 0, 6, null, "legacy");
    }
    public static MapFrame empty(long revision) {
        return new MapFrame(false, 0, "", 0, 0, 0, 0, 0, 0, revision, "");
    }
    public String json(long knownRevision) {
        long sentRevision = revision;
        String map;
        if (tiles == null) {
            map = revision == knownRevision ? "" : ",\"image\":\"" + pngBase64 + "\"";
        } else {
            long since = knownRevision < viewRevision ? -1 : knownRevision;
            int low = 0, high = tiles.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (tiles.get(middle).revision() <= since) low = middle + 1; else high = middle;
            }
            int end = Math.min(low + 128, tiles.size());
            if (end < tiles.size()) sentRevision = tiles.get(end - 1).revision();
            var json = new StringBuilder(",\"tiles\":[");
            for (int i = low; i < end; i++) { if (i > low) json.append(','); json.append(tiles.get(i).json()); }
            map = json.append(']').toString();
        }
        return "{\"active\":" + active + ",\"worldId\":" + worldId + ",\"dimension\":\"" + escape(dimension)
            + "\",\"x\":" + x + ",\"z\":" + z + ",\"yaw\":" + yaw
            + ",\"originX\":" + originX + ",\"originZ\":" + originZ
            + ",\"playerY\":" + playerY + ",\"mapY\":" + mapY + ",\"minY\":" + minY + ",\"maxY\":" + maxY
            + ",\"slice\":" + slice + ",\"manualHeight\":" + manualHeight
            + ",\"viewId\":\"" + escape(viewId) + "\",\"radius\":" + radius + ",\"savedChunks\":" + (tiles == null ? 0 : tiles.size())
            + ",\"size\":" + size + ",\"revision\":" + sentRevision + map + "}";
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }
}
