package dev.minemap.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/** Real HTTP fixture; browser tests load the core and resources from the built mod JAR. */
public final class PhoneServerFixture {
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    private static MapFrame frame(long world, double x, double z, long revision, List<MapTile> tiles) {
        return new MapFrame(true, world, "overworld", x, z, 180, 0, 0, 16, revision, "", 64, 64, -64, 319, false, false,
            "surface", 0, 10, tiles, "fixture-" + world);
    }
    public static void main(String[] args) throws Exception {
        try (var server = new PhoneServer(0);
             var input = new BufferedReader(new InputStreamReader(System.in))) {
            server.publish(frame(1, 15.5, -23.5, 2, List.of(new MapTile(0,-2,1,PNG),new MapTile(1,-2,2,PNG))));
            System.out.println(server.url("127.0.0.1"));
            for (String command; (command = input.readLine()) != null;) {
                switch (command) {
                    case "inactive" -> server.publish(MapFrame.empty(4));
                    case "update" -> server.publish(frame(1, 16.5, -23.5, 3, List.of(new MapTile(0,-2,1,PNG),new MapTile(1,-2,3,PNG))));
                    case "active" -> server.publish(frame(2, 31, 42, 5, List.of(new MapTile(1,2,5,PNG))));
                    case "height20" -> { if (server.viewRequest() == null || server.viewRequest().y() != 20) throw new AssertionError("Height command missing"); }
                    case "auto" -> { if (server.viewRequest() == null || server.viewRequest().y() != null) throw new AssertionError("Auto command missing"); }
                    case "reset" -> server.reset();
                    case "quit" -> { return; }
                    default -> throw new IllegalArgumentException(command);
                }
                System.out.println("ok");
            }
        }
    }
}
