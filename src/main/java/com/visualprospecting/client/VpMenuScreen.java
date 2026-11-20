package com.visualprospecting.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        int top = this.height / 2 - 72;
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 92, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
