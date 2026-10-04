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
            server.publish(new MapFrame(true, 1, "test", -12.5, 10, 90, -16, 0, 16, 8, "abc"));
            var first = get(server, "/state", token);
            assertEquals(200, first.statusCode());
            assertTrue(first.body().contains("\"worldId\":1"));
            assertEquals("no-store", first.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(first.headers().firstValue("Content-Security-Policy").orElseThrow().contains("connect-src 'self'"));
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
    @Test void httpRoutesAndRevisionRecovery() throws Exception {
        try (var server = new PhoneServer(0)) {
            assertFalse(server.connected());
            String token = URI.create(server.url("127.0.0.1")).getFragment();
            assertEquals(404, get(server, "/unknown", null).statusCode());
            var post = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/state"))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(405, client.send(post, HttpResponse.BodyHandlers.ofString()).statusCode());
            server.publish(new MapFrame(true, 2, "overworld", 0, 0, 0, 0, 0, 16, 20, "png"));
            assertTrue(get(server, "/state?revision=bad", token).body().contains("\"image\""));
            assertFalse(get(server, "/state?revision=20", token).body().contains("\"image\""));
            server.publish(MapFrame.empty(21));
            assertTrue(get(server, "/state?revision=20", token).body().contains("\"active\":false"));
            server.publish(new MapFrame(true, 3, "overworld", 10, 10, 0, 0, 0, 16, 22, "new"));
            String next = get(server, "/state?revision=21", token).body();
            assertTrue(next.contains("\"worldId\":3"));
            assertTrue(next.contains("\"image\":\"new\""));
        }
    }
    private HttpResponse<String> post(PhoneServer server, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("X-MineMap-Token", token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void heightAndMarkersRequireTokenAndCurrentWorld() throws Exception {
        try (var server = new PhoneServer(0)) {
            String token = URI.create(server.url("127.0.0.1")).getFragment();
            server.publish(new MapFrame(true, 4, "overworld", 10, 10, 0, 0, 0, 16, 22, "png"));
            assertEquals(401, post(server, "/view?worldId=4&y=20", null).statusCode());
            assertEquals(409, post(server, "/view?worldId=3&y=20", token).statusCode());
            assertEquals(400, post(server, "/view?worldId=4&y=oops", token).statusCode());
            assertEquals(200, post(server, "/view?worldId=4&y=20", token).statusCode());
            assertEquals(20, server.viewRequest().y());
            assertEquals(200, post(server, "/view?worldId=4&y=999", token).statusCode());
            assertEquals(319, server.viewRequest().y());
            assertEquals(200, post(server, "/view?worldId=4&y=auto", token).statusCode()); assertNull(server.viewRequest().y());
            assertEquals(401, post(server, "/markers?worldId=4&x=10&y=20&z=30", null).statusCode());
            assertEquals(400, post(server, "/markers?worldId=4&x=NaN&y=20&z=30", token).statusCode());
            assertEquals(200, post(server, "/markers?worldId=4&x=-10&y=20&z=30&name=Cave&color=%2364b5ff", token).statusCode());
            assertTrue(get(server, "/state", token).body().contains("\"name\":\"Cave\""));
            server.reset(); assertNull(server.viewRequest());
            assertEquals(401, post(server, "/view?worldId=4&y=20", token).statusCode());
        }
    }

}
