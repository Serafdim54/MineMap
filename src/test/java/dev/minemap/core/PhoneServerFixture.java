package dev.minemap.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Real HTTP fixture; browser tests load the core and resources from the built mod JAR. */
public final class PhoneServerFixture {
    private static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
    public static void main(String[] args) throws Exception {
        try (var server = new PhoneServer(0);
             var input = new BufferedReader(new InputStreamReader(System.in))) {
            server.publish(new MapFrame(true, 1, "overworld", 15.5, -23.5, 180, 0, -32, 16, 1, PNG));
            System.out.println(server.url("127.0.0.1"));
            for (String command; (command = input.readLine()) != null;) {
                switch (command) {
                    case "inactive" -> server.publish(MapFrame.empty(2));
                    case "active" -> server.publish(new MapFrame(true, 2, "overworld", 31, 42, 0, 16, 32, 16, 3, PNG));
                    case "reset" -> server.reset();
                    case "quit" -> { return; }
                    default -> throw new IllegalArgumentException(command);
                }
                System.out.println("ok");
            }
        }
    }
}
