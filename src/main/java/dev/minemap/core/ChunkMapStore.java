package dev.minemap.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Disk I/O runs on one worker; the game thread supplies immutable color arrays. */
public final class ChunkMapStore implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "MineMap save"); thread.setDaemon(true); return thread;
    });
    public static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    public CompletableFuture<Map<Long, int[]>> load(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try { return read(path); } catch (IOException e) { throw new CompletionException(e); }
        }, worker);
    }
    private static Map<Long, int[]> read(Path path) throws IOException {
        if (!Files.exists(path)) return new HashMap<>();
        try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            if (in.readInt() != 0x4d4d0001) throw new IOException("Unsupported map cache");
            int count = in.readInt();
            if (count < 0 || count > 200_000) throw new IOException("Invalid map size");
            Map<Long, int[]> result = new HashMap<>();
            for (int n = 0; n < count; n++) {
                long key = in.readLong(); var colors = new int[256];
                for (int i = 0; i < colors.length; i++) colors[i] = in.readInt();
                result.put(key, colors);
            }
            return result;
        }
    }
    public CompletableFuture<Void> save(Path path, Map<Long, int[]> colors) {
        var snapshot = new HashMap<>(colors);
        return CompletableFuture.runAsync(() -> {
            try {
                var merged = read(path); merged.putAll(snapshot);
                if (merged.size() > 200_000) throw new IOException("Map cache too large");
                Files.createDirectories(path.getParent());
                Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))) {
                    out.writeInt(0x4d4d0001); out.writeInt(merged.size());
                    for (var entry : merged.entrySet()) {
                        out.writeLong(entry.getKey());
                        for (int color : entry.getValue()) out.writeInt(color);
                    }
                }
                try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException e) { throw new CompletionException(e); }
        }, worker);
    }
    @Override public void close() {
        worker.shutdown();
        try { worker.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
