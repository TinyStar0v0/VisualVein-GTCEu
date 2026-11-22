package com.visualvein.client.xaero;

import com.visualvein.client.MapSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureManager;
import xaero.map.element.MapElementRenderer;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

public final class XaeroVeinRenderer
        extends MapElementRenderer<XaeroVeinMarker, XaeroRenderContext, XaeroVeinRenderer> {

    public XaeroVeinRenderer() {
        super(new XaeroRenderContext(), new XaeroVeinProvider(XaeroIntegration.markers()),
                new XaeroVeinReader());
    }


    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public boolean renderElement(XaeroVeinMarker marker, boolean hovered, double optionalDepth,
                                 float optionalScale, double partialX, double partialY,
                                 ElementRenderInfo renderInfo, GuiGraphics graphics,
                                 MultiBufferSource.BufferSource buffers,
                                 MultiTextureRenderTypeRendererProvider rendererProvider) {
        XaeroIntegration.setHovered(hovered ? marker : null);
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
            // Destination 64x64, source 16x16: use the source/destination pair overload so the
            // icon is stretched instead of tiled 4x4.
            graphics.blit(XaeroTextures.markerTexture(marker), -32, -32, 64, 64,
                    0.0F, 0.0F, 16, 16, 16, 16);
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            int fillAlpha = (int) (alpha * 255);
            graphics.fill(-32, -32, 32, 32, (fillAlpha << 24) | (marker.color() & 0xFFFFFF));
        }
        graphics.renderOutline(-32, -32, 64, 64, marker.depleted() ? 0xFFFF4444 : 0xFFFFFFFF);

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
        String clean = name.replaceAll("[^\\p{L}\\p{N}]", " ");
        String[] parts = clean.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && result.length() < 3) {
                result.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return result.isEmpty() ? "?" : result.toString();
    }

    @Override
    public void renderElementShadow(XaeroVeinMarker marker, boolean hovered, float optionalScale,
                                    double partialX, double partialY, ElementRenderInfo renderInfo,
                                    GuiGraphics graphics, MultiBufferSource.BufferSource buffers,
                                    MultiTextureRenderTypeRendererProvider rendererProvider) {
    }

    @Override
    public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return location == ElementRenderLocation.WORLD_MAP;
    }

    @Override
    public void preRender(ElementRenderInfo renderInfo, MultiBufferSource.BufferSource buffers,
                          MultiTextureRenderTypeRendererProvider rendererProvider, boolean shadow) {
        if (!shadow) {
            XaeroIntegration.setHovered(null);
        }
    }

    @Override
    public void postRender(ElementRenderInfo renderInfo, MultiBufferSource.BufferSource buffers,
                           MultiTextureRenderTypeRendererProvider rendererProvider, boolean shadow) {
    }

    @Override
    @Deprecated
    public void beforeRender(int location, Minecraft minecraft, GuiGraphics graphics, double cameraX,
                             double cameraZ, double mouseX, double mouseZ, float brightness, double scale,
                             double screenSizeBasedScale, TextureManager textureManager, Font font,
                             MultiBufferSource.BufferSource buffers,
                             MultiTextureRenderTypeRendererProvider rendererProvider, boolean pre) {
    }

    @Override
    @Deprecated
    public void afterRender(int location, Minecraft minecraft, GuiGraphics graphics, double cameraX,
                            double cameraZ, double mouseX, double mouseZ, float brightness, double scale,
                            double screenSizeBasedScale, TextureManager textureManager, Font font,
                            MultiBufferSource.BufferSource buffers,
                            MultiTextureRenderTypeRendererProvider rendererProvider, boolean pre) {
    }

    @Override
    @Deprecated
    public void renderElementPre(int location, XaeroVeinMarker marker, boolean hovered, Minecraft minecraft,
                                 GuiGraphics graphics, double cameraX, double cameraZ, double mouseX,
                                 double mouseZ, float brightness, double scale, double screenSizeBasedScale,
                                 TextureManager textureManager, Font font,
                                 MultiBufferSource.BufferSource buffers,
                                 MultiTextureRenderTypeRendererProvider rendererProvider,
                                 float optionalScale, double partialX, double partialY, boolean cave,
                                 float partialTicks) {
    }

    @Override
    @Deprecated
    public boolean renderElement(int location, XaeroVeinMarker marker, boolean hovered, Minecraft minecraft,
                                 GuiGraphics graphics, double cameraX, double cameraZ, double mouseX,
                                 double mouseZ, float brightness, double scale, double screenSizeBasedScale,
                                 TextureManager textureManager, Font font,
                                 MultiBufferSource.BufferSource buffers,
                                 MultiTextureRenderTypeRendererProvider rendererProvider, int elementIndex,
                                 double optionalDepth, float optionalScale, double partialX, double partialY,
                                 boolean cave, float partialTicks) {
        return renderElement(marker, hovered, optionalDepth, optionalScale, partialX, partialY,
                null, graphics, buffers, rendererProvider);
    }

    @Override
    @Deprecated
    public boolean shouldRender(int location, boolean shadow) {
        return true;
    }
}
