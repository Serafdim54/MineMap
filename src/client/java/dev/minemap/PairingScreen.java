package dev.minemap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import dev.minemap.core.PhoneServer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Map;

final class PairingScreen extends Screen {
    private final PhoneServer server;
    private final String error;
    private final List<String> addresses = PhoneServer.addresses();
    private int selected;
    private BitMatrix qr;
    private int scale, top;
    PairingScreen(PhoneServer server, String error) {
        super(Component.literal("MineMap — телефонная карта")); this.server = server; this.error = error;
    }
    @Override protected void init() {
        top = 42;
        scale = Math.max(1, Math.min(4, (height - 150) / 65));
        if (server == null) {
            addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose()).bounds(width / 2 - 70, height - 30, 140, 20).build()); return;
        }
        updateQr();
        int y = height - 76;
        addRenderableWidget(Button.builder(Component.literal("IP: " + addresses.get(selected)), b -> {
            selected = (selected + 1) % addresses.size(); b.setMessage(Component.literal("IP: " + addresses.get(selected))); updateQr();
        }).bounds(width / 2 - 150, y, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Сбросить QR"), b -> { server.reset(); updateQr(); }).bounds(width / 2 + 45, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Копировать ссылку"), b -> minecraft.keyboardHandler.setClipboard(server.url(addresses.get(selected)))).bounds(width / 2 - 150, y + 24, 190, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose()).bounds(width / 2 + 45, y + 24, 105, 20).build());
    }
    private void updateQr() {
        try { qr = new QRCodeWriter().encode(server.url(addresses.get(selected)), BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 4)); }
        catch (Exception e) { throw new IllegalStateException("QR generation failed", e); }
        scale = Math.max(1, Math.min(4, (height - 150) / qr.getHeight()));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, title, width / 2 - font.width(title) / 2, 14, 0xffffffff, true);
        if (server == null) { g.text(font, "Ошибка сервера: " + error, 20, 60, 0xffff8888, true); return; }
        if (qr != null) {
            int left = (width - qr.getWidth() * scale) / 2;
            for (int y = 0; y < qr.getHeight(); y++) for (int x = 0; x < qr.getWidth(); x++)
                g.fill(left + x * scale, top + y * scale, left + (x + 1) * scale, top + (y + 1) * scale, qr.get(x, y) ? 0xff000000 : 0xffffffff);
        }
        String status = server.connected() ? "Телефон подключён" : "Подключи телефон к Wi-Fi / хотспоту и сканируй QR";
        g.text(font, status, width / 2 - font.width(status) / 2, height - 92, server.connected() ? 0xff80eeaa : 0xffffffff, true);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(null); }
}
