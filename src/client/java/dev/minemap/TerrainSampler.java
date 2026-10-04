package dev.minemap;

import dev.minemap.core.MapFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/** Reads only client-loaded chunks, on the client tick thread. */
final class TerrainSampler {
    private static final int RADIUS = 6, SIZE = (RADIUS * 2 + 1) * 16;
    private final Map<Long, int[]> tiles = new HashMap<>();
    private final List<int[]> offsets = new ArrayList<>();
    private ClientLevel level;
    private int cursor, ticks, originX, originZ;
    private long revision;
    private String image = "";
    TerrainSampler() {
        for (int z = -RADIUS; z <= RADIUS; z++) for (int x = -RADIUS; x <= RADIUS; x++) offsets.add(new int[]{x, z});
        offsets.sort(Comparator.comparingInt(p -> p[0] * p[0] + p[1] * p[1]));
    }
    MapFrame tick(Minecraft client) {
        if (client.level != level) {
            level = client.level; tiles.clear(); cursor = ticks = 0; image = ""; revision++;
        }
        if (level == null || client.player == null) return MapFrame.empty(revision);
        int centerX = Math.floorDiv(client.player.getBlockX(), 16), centerZ = Math.floorDiv(client.player.getBlockZ(), 16);
        for (int n = 0; n < 2; n++) {
            int[] offset = offsets.get(cursor++ % offsets.size());
            int cx = centerX + offset[0], cz = centerZ + offset[1];
            if (!level.getChunkSource().hasChunk(cx, cz)) { tiles.remove(key(cx, cz)); continue; }
            var colors = new int[256];
            var pos = new BlockPos.MutableBlockPos();
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int wx = cx * 16 + x, wz = cz * 16 + z;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                pos.set(wx, y, wz);
                var state = level.getBlockState(pos);
                int rgb = state.getMapColor(level, pos).col;
                // Height-based subtle shading. Unknown/empty space remains transparent.
                double shade = 0.82 + Math.floorMod(y, 8) * 0.025;
                int r = (int) (((rgb >> 16) & 255) * shade);
                int g = (int) (((rgb >> 8) & 255) * shade);
                int b = (int) ((rgb & 255) * shade);
                colors[z * 16 + x] = rgb == 0 ? 0 : 0xff000000 | r << 16 | g << 8 | b;
            }
            tiles.put(key(cx, cz), colors);
        }
        tiles.keySet().removeIf(k -> Math.abs((int)(k >> 32) - centerX) > RADIUS + 1 || Math.abs((int)(long)k - centerZ) > RADIUS + 1);
        if (++ticks % 20 == 0 || image.isEmpty()) {
            var bitmap = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            originX = (centerX - RADIUS) * 16; originZ = (centerZ - RADIUS) * 16;
            for (int[] offset : offsets) {
                int[] tile = tiles.get(key(centerX + offset[0], centerZ + offset[1]));
                if (tile != null) bitmap.setRGB((offset[0] + RADIUS) * 16, (offset[1] + RADIUS) * 16, 16, 16, tile, 0, 16);
            }
            try (var bytes = new ByteArrayOutputStream()) {
                ImageIO.write(bitmap, "png", bytes);
                image = Base64.getEncoder().encodeToString(bytes.toByteArray()); revision++;
            } catch (IOException e) { throw new IllegalStateException("Unable to encode map", e); }
        }
        return new MapFrame(true, level.dimension().toString(), client.player.getX(), client.player.getZ(),
            client.player.getYRot(), originX, originZ, SIZE, revision, image);
    }
    private static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }
}
