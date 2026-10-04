package dev.minemap.core;

/** Immutable frame published by the game thread and read by HTTP workers. */
public record MapFrame(boolean active, String dimension, double x, double z, float yaw,
                       int originX, int originZ, int size, long revision, String pngBase64) {
    public static MapFrame empty(long revision) {
        return new MapFrame(false, "", 0, 0, 0, 0, 0, 0, revision, "");
    }
    public String json(long knownRevision) {
        String map = revision == knownRevision ? "" : ",\"image\":\"" + pngBase64 + "\"";
        return "{\"active\":" + active + ",\"dimension\":\"" + escape(dimension)
            + "\",\"x\":" + x + ",\"z\":" + z + ",\"yaw\":" + yaw
            + ",\"originX\":" + originX + ",\"originZ\":" + originZ
            + ",\"size\":" + size + ",\"revision\":" + revision + map + "}";
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r");
    }
}
