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

public final class XaeroFluidRenderer
        extends MapElementRenderer<XaeroFluidMarker, XaeroRenderContext, XaeroFluidRenderer> {

    public XaeroFluidRenderer() {
        super(new XaeroRenderContext(), new XaeroFluidProvider(XaeroIntegration.fluidMarkers()),
                new XaeroFluidReader());
    }

    @Override
    public int getOrder() {
        return 45;
    }

    @Override
    public boolean renderElement(XaeroFluidMarker marker, boolean hovered, double optionalDepth,
                                 float optionalScale, double partialX, double partialY,
                                 ElementRenderInfo renderInfo, GuiGraphics graphics,
                                 MultiBufferSource.BufferSource buffers,
                                 MultiTextureRenderTypeRendererProvider rendererProvider) {
        if (!XaeroDisplay.showFluid()) {
            return false;
        }
        XaeroIntegration.setHoveredFluid(hovered ? marker : null);
        boolean search = MapSearch.active();
        boolean match = !search || MapSearch.matchesFluid(marker.record());
        float alpha = search && !match ? 0.28F : 0.62F;
        double mapScale = renderInfo == null ? 3.0 : Math.max(1.0, renderInfo.scale);
        int size = Math.max(8, (int) Math.round(16.0 * mapScale));
        int half = size / 2;
        int fillAlpha = (int) (alpha * 255);
        int fill = (fillAlpha << 24) | (marker.color() & 0xFFFFFF);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(partialX, partialY, 0.0);
        // One full 16x16 chunk tint, not a marker icon.
        graphics.fill(-half, -half, size - half, size - half, fill);
        // Yellow chunk separation grid for the 8x8 GT fluid vein region.
        graphics.fill(-half, -half, size - half, -half + 1, 0xE0FFD24A);
        graphics.fill(-half, size - half - 1, size - half, size - half, 0xE0FFD24A);
        graphics.fill(-half, -half, -half + 1, size - half, 0xE0FFD24A);
        graphics.fill(size - half - 1, -half, size - half, size - half, 0xE0FFD24A);
        pose.popPose();
        return true;
    }

    @Override
    public void renderElementShadow(XaeroFluidMarker marker, boolean hovered, float optionalScale,
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
            XaeroIntegration.setHoveredFluid(null);
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
    public void renderElementPre(int location, XaeroFluidMarker marker, boolean hovered, Minecraft minecraft,
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
    public boolean renderElement(int location, XaeroFluidMarker marker, boolean hovered, Minecraft minecraft,
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
