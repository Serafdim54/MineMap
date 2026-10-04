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
            var changed = tiles.stream().filter(t -> t.revision() > since).limit(129).toList();
            int count = Math.min(128, changed.size());
            if (changed.size() > 128) sentRevision = changed.get(count - 1).revision();
            var json = new StringBuilder(",\"tiles\":[");
            for (int i = 0; i < count; i++) { if (i > 0) json.append(','); json.append(changed.get(i).json()); }
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
