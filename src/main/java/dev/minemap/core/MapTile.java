package dev.minemap.core;

public record MapTile(int x, int z, long revision, String image) {
    public String json() {
        return "{\"x\":" + x + ",\"z\":" + z + ",\"revision\":" + revision + ",\"image\":\"" + image + "\"}";
    }
}
