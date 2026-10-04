package dev.minemap.core;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small, atomic waypoint files; called only from HTTP workers, never the game thread. */
public final class PointStore {
    private final Path root;
    private final Map<String, List<MapPoint>> cache = new HashMap<>();
    public PointStore(Path root) { this.root = root; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Path file(String key) { return root.resolve(hash(key) + ".points"); }
    public synchronized List<MapPoint> get(String key) throws IOException {
        if (cache.containsKey(key)) return cache.get(key);
        var points = new ArrayList<MapPoint>();
        Path file = root == null ? null : file(key);
        if (file != null && Files.exists(file)) try (var in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != 0x4d500001) throw new IOException("Unsupported waypoint file");
            int count = in.readInt(); if (count < 0 || count > 200) throw new IOException("Invalid waypoint count");
            for (int i = 0; i < count; i++) points.add(new MapPoint(in.readUTF(), in.readUTF(), in.readUTF(), in.readDouble(), in.readInt(), in.readDouble()));
        }
        var result = List.copyOf(points); cache.put(key, result); return result;
    }
    public synchronized void put(String key, MapPoint point) throws IOException {
        var points = new ArrayList<>(get(key));
        points.removeIf(p -> p.id().equals(point.id()));
        if (points.size() >= 200) throw new IllegalArgumentException("Too many markers");
        points.add(point); write(key, points);
    }
    public synchronized void delete(String key, String id) throws IOException {
        var points = new ArrayList<>(get(key)); points.removeIf(p -> p.id().equals(id)); write(key, points);
    }
    private void write(String key, List<MapPoint> points) throws IOException {
        if (root == null) { cache.put(key, List.copyOf(points)); return; }
        Files.createDirectories(root);
        Path file = file(key), temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var out = new DataOutputStream(Files.newOutputStream(temp))) {
            out.writeInt(0x4d500001); out.writeInt(points.size());
            for (var p : points) { out.writeUTF(p.id()); out.writeUTF(p.name()); out.writeUTF(p.color()); out.writeDouble(p.x()); out.writeInt(p.y()); out.writeDouble(p.z()); }
        }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        cache.put(key, List.copyOf(points));
    }
}
