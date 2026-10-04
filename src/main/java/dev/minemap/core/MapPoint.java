package dev.minemap.core;

public record MapPoint(String id, String name, String color, double x, int y, double z) {
    public String json() {
        return "{\"id\":\"" + escape(id) + "\",\"name\":\"" + escape(name) + "\",\"color\":\"" + color
            + "\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
    }
    private static String escape(String text) {
        var out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.toString();
    }
}
