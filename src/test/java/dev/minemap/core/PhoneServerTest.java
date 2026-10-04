package dev.minemap.core;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

class PhoneServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpResponse<String> get(PhoneServer server, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path));
        if (token != null) request.header("X-MineMap-Token", token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void pairingAndResetProtectWorldData() throws Exception {
        try (var server = new PhoneServer(0)) {
            String token = URI.create(server.url("127.0.0.1")).getFragment();
            assertEquals(200, get(server, "/", null).statusCode());
            assertEquals(401, get(server, "/state", null).statusCode());
            assertEquals(401, get(server, "/state", "wrong").statusCode());
            server.publish(new MapFrame(true, "test", -12.5, 10, 90, -16, 0, 16, 8, "abc"));
            var first = get(server, "/state", token);
            assertEquals(200, first.statusCode());
            assertTrue(first.body().contains("\"image\":\"abc\""));
            assertTrue(first.body().contains("\"x\":-12.5"));
            assertFalse(get(server, "/state?revision=8", token).body().contains("\"image\""));
            assertTrue(server.connected());
            server.reset();
            assertFalse(server.connected());
            assertEquals(401, get(server, "/state", token).statusCode());
            assertEquals(200, get(server, "/state", URI.create(server.url("127.0.0.1")).getFragment()).statusCode());
        }
    }
    @Test void leavingWorldClearsMap() {
        assertTrue(MapFrame.empty(4).json(3).contains("\"image\":\"\""));
        assertTrue(MapFrame.empty(4).json(3).contains("\"active\":false"));
    }
}
