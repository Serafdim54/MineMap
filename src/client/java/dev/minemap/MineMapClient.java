package dev.minemap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minemap.core.PhoneServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class MineMapClient implements ClientModInitializer {
    private PhoneServer server;
    private volatile TerrainSampler sampler;
    private static volatile MineMapClient instance;
    public static void chunkChanged(int x, int z) {
        var current = instance;
        if (current != null && current.sampler != null) current.sampler.markChanged(x, z);
    }
    private String error;
    private final boolean smoke = Boolean.getBoolean("minemap.smoke");
    private int smokeTicks;
    @Override public void onInitializeClient() {
        instance = this;
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minemap", "controls"));
        var open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minemap.connect", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                if (server == null) {
                    try { server = new PhoneServer(0, FabricLoader.getInstance().getGameDir().resolve("minemap")); sampler = new TerrainSampler(); error = null; }
                    catch (Exception e) { error = e.getMessage(); }
                }
                client.gui.setScreen(new PairingScreen(server, error));
            }
            if (server != null) server.publish(sampler.tick(client, server.viewRequest()));
            if (smoke && server != null && ++smokeTicks == 80) {
                org.slf4j.LoggerFactory.getLogger("MineMap").info("MINEMAP_SMOKE_OK"); client.stop();
            }
        });
        if (smoke) ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            try {
                // Force transformation of the chunk class, then render the real pairing screen.
                Class.forName("net.minecraft.world.level.chunk.LevelChunk");
                server = new PhoneServer(0); sampler = new TerrainSampler();
                client.gui.setScreen(new PairingScreen(server, null));
            } catch (Exception e) { throw new IllegalStateException("MineMap startup smoke failed", e); }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { if (sampler != null) sampler.close(); if (server != null) server.close(); });
    }
}
