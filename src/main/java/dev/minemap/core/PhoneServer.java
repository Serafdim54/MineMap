package dev.minemap.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** No game objects may be accessed from this class. */
public final class PhoneServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService workers;
    private static final class Session {
        final String token = newToken();
        volatile long lastSeen;
    }
    private volatile Session session = new Session();
    private volatile MapFrame frame = MapFrame.empty(0);
    private final byte[] html;
    public record ViewRequest(long worldId, Integer y) { }
    private volatile ViewRequest view;
    private final PointStore points;
    public ViewRequest viewRequest() { return view; }

    public PhoneServer(int port) throws IOException { this(port, null); }
    public PhoneServer(int port, Path dataRoot) throws IOException {
        points = new PointStore(dataRoot == null ? null : dataRoot.resolve("markers"));
        try (var stream = PhoneServer.class.getResourceAsStream("/web/index.html")) {
            if (stream == null) throw new IOException("Missing phone interface");
            html = stream.readAllBytes();
        }
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 8);
        workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), runnable -> {
                var thread = new Thread(runnable, "MineMap HTTP");
                thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(workers);
        server.createContext("/", this::handle);
        server.start();
    }
    public void publish(MapFrame frame) { this.frame = frame; }
    public int port() { return server.getAddress().getPort(); }
    public boolean connected() {
        long seen = session.lastSeen;
        return seen != 0 && System.nanoTime() - seen < TimeUnit.SECONDS.toNanos(3);
    }
    public void reset() { session = new Session(); view = null; }
    public String url(String address) { return "http://" + address + ":" + port() + "/#" + session.token; }
    public static List<String> addresses() {
        var addresses = new ArrayList<String>();
        try {
            for (var interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                var nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (var ips = nic.getInetAddresses(); ips.hasMoreElements();) {
                    var ip = ips.nextElement();
                    if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) addresses.add(ip.getHostAddress());
                }
            }
        } catch (SocketException ignored) { }
        addresses.sort(Comparator.comparing((String s) -> !s.equals("192.168.137.1"))
            .thenComparing(s -> !s.startsWith("192.168.")));
        if (addresses.isEmpty()) addresses.add("127.0.0.1");
        return List.copyOf(addresses);
    }
    private static Map<String, String> query(String raw) {
        var result = new HashMap<String, String>();
        if (raw != null) for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            boolean command = path.equals("/view") || path.equals("/markers");
            if (!exchange.getRequestMethod().equals(command ? "POST" : "GET")) {
                respond(exchange, 405, "text/plain", "Unsupported method".getBytes(StandardCharsets.UTF_8)); return;
            }
            if (path.equals("/")) { respond(exchange, 200, "text/html; charset=utf-8", html); return; }
            if (!path.equals("/state") && !command) { respond(exchange, 404, "text/plain", new byte[0]); return; }
            Session current = session;
            String supplied = exchange.getRequestHeaders().getFirst("X-MineMap-Token");
            if (supplied == null || !java.security.MessageDigest.isEqual(current.token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                respond(exchange, 401, "text/plain", "Pair again".getBytes(StandardCharsets.UTF_8)); return;
            }
            current.lastSeen = System.nanoTime();
            MapFrame snapshot = frame;
            try {
                var args = query(exchange.getRequestURI().getRawQuery());
                if (command) {
                    if (!snapshot.active() || Long.parseLong(args.getOrDefault("worldId", "-1")) != snapshot.worldId()) {
                        respond(exchange, 409, "text/plain", "World changed".getBytes(StandardCharsets.UTF_8)); return;
                    }
                    if (path.equals("/view")) {
                        String y = args.getOrDefault("y", "auto");
                        view = new ViewRequest(snapshot.worldId(), y.equals("auto") ? null : Math.clamp(Integer.parseInt(y), snapshot.minY(), snapshot.maxY()));
                    } else if (args.getOrDefault("action", "add").equals("delete")) {
                        points.delete(snapshot.mapKey(), args.getOrDefault("id", ""));
                    } else {
                        String name = args.getOrDefault("name", "Метка").strip(), color = args.getOrDefault("color", "#ff6b6b");
                        double x = Double.parseDouble(args.getOrDefault("x", "NaN")), z = Double.parseDouble(args.getOrDefault("z", "NaN"));
                        int y = Integer.parseInt(args.getOrDefault("y", Integer.toString(snapshot.playerY())));
                        if (name.isEmpty() || name.length() > 64 || !color.matches("#[0-9a-fA-F]{6}") || !Double.isFinite(x) || !Double.isFinite(z)
                            || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < snapshot.minY() || y > snapshot.maxY()) throw new IllegalArgumentException("Invalid marker");
                        points.put(snapshot.mapKey(), new MapPoint(UUID.randomUUID().toString(), name, color, x, y, z));
                    }
                    respond(exchange, 200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)); return;
                }
                long revision = -1;
                try { revision = Long.parseLong(args.getOrDefault("revision", "-1")); } catch (NumberFormatException ignored) { }
                String json = snapshot.json(revision);
                String markers = points.get(snapshot.mapKey()).stream().map(MapPoint::json).collect(java.util.stream.Collectors.joining(",", "[", "]"));
                json = json.substring(0, json.length() - 1) + ",\"markers\":" + markers + "}";
                respond(exchange, 200, "application/json", json.getBytes(StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, "text/plain", "Invalid request".getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private void respond(HttpExchange exchange, int status, String type, byte[] data) throws IOException {
        var h = exchange.getResponseHeaders();
        h.set("Content-Type", type); h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff"); h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'self'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
    }
    private static String newToken() {
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    @Override public void close() { server.stop(0); workers.shutdownNow(); }
}
