package dev.minemap.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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

    public PhoneServer(int port) throws IOException {
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
    public void reset() { session = new Session(); }
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
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("GET")) { respond(exchange, 405, "text/plain", "GET only".getBytes(StandardCharsets.UTF_8)); return; }
            switch (exchange.getRequestURI().getPath()) {
                case "/" -> respond(exchange, 200, "text/html; charset=utf-8", html);
                case "/state" -> {
                    // A request from the old session cannot mark the new session connected.
                    Session current = session;
                    String supplied = exchange.getRequestHeaders().getFirst("X-MineMap-Token");
                    if (supplied == null || !java.security.MessageDigest.isEqual(current.token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                        respond(exchange, 401, "text/plain", "Pair again".getBytes(StandardCharsets.UTF_8)); return;
                    }
                    long revision = -1;
                    var query = exchange.getRequestURI().getRawQuery();
                    if (query != null && query.startsWith("revision=")) {
                        try { revision = Long.parseLong(query.substring(9)); } catch (NumberFormatException ignored) { }
                    }
                    current.lastSeen = System.nanoTime();
                    respond(exchange, 200, "application/json", frame.json(revision).getBytes(StandardCharsets.UTF_8));
                }
                default -> respond(exchange, 404, "text/plain", new byte[0]);
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
