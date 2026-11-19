package com.visualprospecting.client;

import com.visualprospecting.data.VeinGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Small in-game control panel opened by the JourneyMap VP button or the V key.
 */
public class VpMenuScreen extends Screen {

    private static final int WIDTH = 240;

    public VpMenuScreen() {
        super(Component.translatable("vp.menu.title"));
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - WIDTH / 2;
        int top = this.height / 2 - 90;
        int y = top;

        addRenderableWidget(Button.builder(overlayLabel(), button -> {
            ClientHooks.toggleOverlay.run();
            refresh();
        }).bounds(left, y, WIDTH, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(label("vp.menu.labels", ClientHooks.labelsEnabled.getAsBoolean()),
                button -> {
                    ClientHooks.toggleLabels.run();
                    refresh();
                }).bounds(left, y, WIDTH, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(label("vp.menu.minimap", ClientHooks.minimapEnabled.getAsBoolean()),
                button -> {
                    ClientHooks.toggleMinimap.run();
                    refresh();
                }).bounds(left, y, WIDTH, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(label("vp.menu.fluid", ClientHooks.fluidEnabled.getAsBoolean()),
                button -> {
                    ClientHooks.toggleFluid.run();
                    refresh();
                }).bounds(left, y, WIDTH, 20).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.translatable("vp.menu.scan"), button -> {
            ClientHooks.scanNearby.run();
            onClose();
        }).bounds(left, y, WIDTH, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("vp.menu.waypoint"), button -> {
            ClientHooks.createWaypoint.run();
        }).bounds(left, y, WIDTH, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("vp.menu.close"), button -> onClose())
                .bounds(left, y, WIDTH, 20).build());
    }

    private void refresh() {
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }

    private static Component overlayLabel() {
        return label("vp.menu.overlay", ClientHooks.overlayEnabled.getAsBoolean());
    }

    private static Component label(String key, boolean on) {
        return Component.translatable(key).copy().append(Component.literal(": "))
                .append(Component.translatable(on ? "vp.menu.on" : "vp.menu.off"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 112, 0xFFFFFF);
        drawNearest(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawNearest(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ClientVeinCache.DimensionData data = ClientVeinCache.get(minecraft.level.dimension().location());
        if (data == null || data.size() == 0) {
            return;
        }
        List<com.visualprospecting.data.VeinRecord> marked = new java.util.ArrayList<>();
        for (com.visualprospecting.data.VeinRecord record : data.all()) {
            if (record.marked) {
                marked.add(record);
            }
        }
        if (marked.isEmpty()) {
            return;
        }
        List<VeinGroup> groups = VeinGroup.group(marked, false, null);
        groups.sort(Comparator.comparingDouble(group -> {
            double dx = group.primary().centerX - minecraft.player.getX();
            double dz = group.primary().centerZ - minecraft.player.getZ();
            return dx * dx + dz * dz;
        }));
        int left = this.width / 2 - WIDTH / 2;
        int y = this.height / 2 + 65;
        graphics.drawString(this.font, Component.translatable("vp.menu.nearest"), left, y, 0xAAAAAA);
        y += 12;
        for (int i = 0; i < Math.min(5, groups.size()); i++) {
            VeinGroup group = groups.get(i);
            int distance = (int) Math.round(Math.hypot(
                    group.primary().centerX - minecraft.player.getX(),
                    group.primary().centerZ - minecraft.player.getZ()));
            graphics.drawString(this.font,
                    group.compactName().copy().append(Component.literal(
                            "  " + group.primary().centerX + ", " + group.primary().centerZ + "  " + distance + "m")),
                    left, y, 0xFFFFFF);
            y += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
