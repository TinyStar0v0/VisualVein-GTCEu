package com.visualvein.client.xaero;

import com.visualvein.client.MapSearch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderer;

public final class XaeroFluidMinimapRenderer
        extends MinimapElementRenderer<XaeroFluidMarker, XaeroRenderContext> {

    public XaeroFluidMinimapRenderer() {
        super(new XaeroFluidMinimapReader(),
                new XaeroFluidMinimapProvider(XaeroIntegration.fluidMinimapMarkers()),
                new XaeroRenderContext());
    }

    @Override
    public int getOrder() {
        return 45;
    }

    @Override
    public boolean renderElement(XaeroFluidMarker marker, boolean highlighted, boolean outOfBounds,
                                 double optionalDepth, float optionalScale, double partialX, double partialY,
                                 MinimapElementRenderInfo renderInfo, GuiGraphics graphics,
                                 MultiBufferSource.BufferSource buffers) {
        if (!XaeroDisplay.showFluid()) {
            return false;
        }
        boolean search = MapSearch.active();
        boolean match = !search || MapSearch.matchesFluid(marker.record());
        int alpha = search && !match ? 0x30 : 0xB0;
        int fill = (alpha << 24) | (marker.color() & 0xFFFFFF);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(partialX, partialY, 0.0);
        graphics.fill(-6, -6, 6, 6, fill);
        graphics.renderOutline(-6, -6, 12, 12, 0xFFFFD24A);
        pose.popPose();
        return true;
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
