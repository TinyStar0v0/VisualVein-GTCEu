package com.visualvein.client.xaero;

import com.visualvein.client.MapSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderer;

public final class XaeroMinimapRenderer
        extends MinimapElementRenderer<XaeroVeinMarker, XaeroRenderContext> {

    public XaeroMinimapRenderer() {
        super(new XaeroMinimapReader(), new XaeroMinimapProvider(XaeroIntegration.markers()),
                new XaeroRenderContext());
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public boolean renderElement(XaeroVeinMarker marker, boolean highlighted, boolean outOfBounds,
                                 double optionalDepth, float optionalScale, double partialX, double partialY,
                                 MinimapElementRenderInfo renderInfo, GuiGraphics graphics,
                                 MultiBufferSource.BufferSource buffers) {
        if (!XaeroDisplay.showOre()) {
            return false;
        }
        boolean search = MapSearch.active();
        boolean match = !search || MapSearch.matchesOre(marker.group());
        float alpha = search && !match ? 0.22F : 1.0F;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(partialX, partialY, 0.0);
        if (XaeroDisplay.texture()) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(alpha, alpha, alpha, 1.0F);
            graphics.blit(XaeroTextures.markerTexture(marker), -8, -8, 0, 0, 16, 16, 16, 16);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            int fillAlpha = (int) (alpha * 255);
            graphics.fill(-8, -8, 8, 8, (fillAlpha << 24) | (marker.color() & 0xFFFFFF));
        }
        graphics.renderOutline(-6, -6, 12, 12,
                marker.depleted() ? 0xFFFF4444 : 0xFFFFFFFF);
        Font font = Minecraft.getInstance().font;
        String initials = initials(marker.name());
        if (search && match) {
            graphics.drawString(font, initials, -font.width(initials) / 2, -4,
                    0xFFFFFF, false);
        }
        pose.popPose();
        return true;
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.replaceAll("[^\\p{L}\\p{N}]", " ").trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && result.length() < 3) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return result.isEmpty() ? "?" : result.toString();
    }

    @Override
    public void preRender(MinimapElementRenderInfo renderInfo, MultiBufferSource.BufferSource buffers,
                          MultiTextureRenderTypeRendererProvider rendererProvider) {
    }

    @Override
    public void postRender(MinimapElementRenderInfo renderInfo, MultiBufferSource.BufferSource buffers,
                           MultiTextureRenderTypeRendererProvider rendererProvider) {
    }

    @Override
    public boolean shouldRender(MinimapElementRenderLocation location) {
        return location == MinimapElementRenderLocation.IN_MINIMAP
                || location == MinimapElementRenderLocation.OVER_MINIMAP;
    }
}
