package dev.minemap;

import dev.minemap.core.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** World reads stay on the game thread; disk I/O uses a worker. */
final class TerrainSampler implements AutoCloseable {
    private final ChunkMapStore store = new ChunkMapStore();
    private final Map<Long, int[]> colors = new HashMap<>();
    private final Map<Long, MapTile> tiles = new HashMap<>();
    private List<MapTile> snapshot = List.of();
    private final List<int[]> offsets = new ArrayList<>();
    private final Set<Long> changedChunks = ConcurrentHashMap.newKeySet();
    void markChanged(int x, int z) { if (changedChunks.size() < 4096) changedChunks.add(ChunkMapStore.key(x, z)); }
    private ClientLevel level;
    private int cursor, ticks, radius;
    private long revision, worldId, viewRevision;
    private String viewId = "", mapKey = "";
    private Path file;
    private boolean dirty;
    private CompletableFuture<Map<Long, int[]>> loading;
    private Iterator<Map.Entry<Long, int[]>> restoring;
    private static void log(Throwable error) { LoggerFactory.getLogger("MineMap").warn("Unable to read/save map cache", error); }
    private void save() {
        if (dirty && file != null) { store.save(file, colors).exceptionally(error -> { log(error); return null; }); dirty = false; }
    }
    private void selectView(String key, String view) {
        save(); colors.clear(); tiles.clear(); changedChunks.clear(); snapshot = List.of(); restoring = null;
        mapKey = key; viewId = view; cursor = 0; viewRevision = ++revision;
        file = FabricLoader.getInstance().getGameDir().resolve("minemap/maps").resolve(key).resolve(view + ".map");
        loading = store.load(file);
    }
    private String worldKey(Minecraft client) {
        String world;
        if (client.getSingleplayerServer() != null) world = "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath();
        else if (client.getCurrentServer() != null) world = "server:" + client.getCurrentServer().ip;
        else world = "session:" + worldId;
        return PointStore.hash(world + "|" + level.dimension());
    }
    private void setRadius(int value) {
        if (value == radius) return;
        radius = value; offsets.clear(); cursor = 0;
        for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++) offsets.add(new int[]{x, z});
        offsets.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1]));
    }
    private void put(int cx, int cz, int[] pixels, boolean changed) {
        long key = ChunkMapStore.key(cx, cz);
        if (Arrays.equals(colors.get(key), pixels)) return;
        try {
            var bitmap = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            bitmap.setRGB(0, 0, 16, 16, pixels, 0, 16);
            try (var bytes = new ByteArrayOutputStream()) {
                ImageIO.write(bitmap, "png", bytes);
                colors.put(key, pixels);
                tiles.put(key, new MapTile(cx, cz, ++revision, Base64.getEncoder().encodeToString(bytes.toByteArray())));
            }
            dirty |= changed;
        } catch (IOException e) { log(e); }
    }
    private void sample(int cx, int cz, HeightView.Selection selection, int minY, int maxY) {
        if (!level.getChunkSource().hasChunk(cx, cz)) return;
        int[] pixels = new int[256]; var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            if (selection.slice()) {
                pixels[z * 16 + x] = HeightView.cave(new HeightView.Column() {
                    private net.minecraft.world.level.block.state.BlockState at(int y) { pos.set(wx, y, wz); return level.getBlockState(pos); }
                    public boolean solid(int y) { return !at(y).getCollisionShape(level, pos).isEmpty(); }
                    public boolean fluid(int y) { return !at(y).getFluidState().isEmpty(); }
                    public int color(int y) { return at(y).getMapColor(level, pos).col; }
                }, selection.y(), minY, maxY);
            } else {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1; pos.set(wx, y, wz);
                int rgb = level.getBlockState(pos).getMapColor(level, pos).col;
                pixels[z * 16 + x] = rgb == 0 ? 0 : HeightView.shade(rgb, .82 + Math.floorMod(y, 8) * .025);
            }
        }
        put(cx, cz, pixels, true);
    }
    MapFrame tick(Minecraft client, PhoneServer.ViewRequest request) {
        if (client.level != level) {
            save(); level = client.level; worldId++; revision++; viewId = ""; ticks = 0;
            colors.clear(); tiles.clear(); snapshot = List.of(); loading = null; restoring = null;
        }
        if (level == null || client.player == null) return MapFrame.empty(revision);
        int minY = level.getMinY(), maxY = minY + level.getHeight() - 1;
        int playerY = client.player.getBlockY();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, client.player.getBlockX(), client.player.getBlockZ()) - 1;
        Integer manualY = request != null && request.worldId() == worldId ? request.y() : null;
        var selection = HeightView.select(playerY, surfaceY, level.dimension().equals(Level.NETHER), manualY, minY, maxY);
        String nextView = selection.slice() ? "y" + selection.y() : "surface";
        if (!nextView.equals(viewId)) selectView(worldKey(client), nextView);
        setRadius(Math.clamp(client.options.renderDistance().get(), 2, 64));
        int centerX = Math.floorDiv(client.player.getBlockX(), 16), centerZ = Math.floorDiv(client.player.getBlockZ(), 16);
        long before = revision;
        if (loading != null && loading.isDone()) {
            try { restoring = loading.join().entrySet().iterator(); } catch (Exception e) { log(e); }
            loading = null;
        }
        long deadline = System.nanoTime() + 3_000_000;
        for (int n = 0; restoring != null && restoring.hasNext() && n < 8 && System.nanoTime() < deadline; n++) {
            var entry = restoring.next();
            if (!colors.containsKey(entry.getKey())) put((int)(entry.getKey() >> 32), (int)(long)entry.getKey(), entry.getValue(), false);
        }
        // Refresh the interacted-with chunk frequently; sweep all visible chunks for remote changes.
        if (ticks % 4 == 0 && client.hitResult instanceof BlockHitResult hit) {
            int cx = Math.floorDiv(hit.getBlockPos().getX(), 16), cz = Math.floorDiv(hit.getBlockPos().getZ(), 16);
            sample(cx, cz, selection, minY, maxY);
        }
        var dirtyChunks = changedChunks.iterator();
        for (int n = 0; n < 2 && dirtyChunks.hasNext() && System.nanoTime() < deadline; n++) {
            long key = dirtyChunks.next(); changedChunks.remove(key);
            sample((int)(key >> 32), (int)key, selection, minY, maxY);
        }
        for (int n = 0; n < 2 && (n == 0 || System.nanoTime() < deadline); n++) {
            int[] offset = offsets.get(cursor); cursor = (cursor + 1) % offsets.size();
            sample(centerX + offset[0], centerZ + offset[1], selection, minY, maxY);
        }
        if (revision != before) snapshot = tiles.values().stream().sorted(Comparator.comparingLong(MapTile::revision)).toList();
        if (++ticks % 200 == 0 && restoring == null) save();
        if (restoring != null && !restoring.hasNext()) restoring = null;
        return new MapFrame(true, worldId, level.dimension().toString(), client.player.getX(), client.player.getZ(), client.player.getYRot(),
            0, 0, 16, revision, "", playerY, selection.y(), minY, maxY, selection.slice(), selection.manual(), viewId, viewRevision, radius, snapshot, mapKey);
    }
    @Override public void close() { save(); store.close(); }
}
