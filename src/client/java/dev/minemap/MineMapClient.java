package dev.minemap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.minemap.core.PhoneServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MineMapClient implements ClientModInitializer {
    private PhoneServer server;
    private TerrainSampler sampler;
    private String error;
    @Override public void onInitializeClient() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minemap", "controls"));
        var open = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minemap.connect", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                if (server == null) {
                    try { server = new PhoneServer(0); sampler = new TerrainSampler(); error = null; }
                    catch (Exception e) { error = e.getMessage(); }
                }
                client.gui.setScreen(new PairingScreen(server, error));
            }
            if (server != null) server.publish(sampler.tick(client));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { if (server != null) server.close(); });
    }
}
