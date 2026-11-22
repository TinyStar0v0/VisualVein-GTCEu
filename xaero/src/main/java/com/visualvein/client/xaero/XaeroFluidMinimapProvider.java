package com.visualvein.client.xaero;

import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;

import java.util.Iterator;
import java.util.List;

public final class XaeroFluidMinimapProvider
        extends MinimapElementRenderProvider<XaeroFluidMarker, XaeroRenderContext> {

    private final List<XaeroFluidMarker> markers;
    private Iterator<XaeroFluidMarker> iterator;

    public XaeroFluidMinimapProvider(List<XaeroFluidMarker> markers) {
        this.markers = markers;
    }

    @Override
    public void begin(MinimapElementRenderLocation location, XaeroRenderContext context) {
        iterator = markers.iterator();
    }

    @Override
    public boolean hasNext(MinimapElementRenderLocation location, XaeroRenderContext context) {
        return iterator != null && iterator.hasNext();
    }

    @Override
    public XaeroFluidMarker getNext(MinimapElementRenderLocation location, XaeroRenderContext context) {
        return iterator.next();
    }

    @Override
    public void end(MinimapElementRenderLocation location, XaeroRenderContext context) {
        iterator = null;
    }
}
