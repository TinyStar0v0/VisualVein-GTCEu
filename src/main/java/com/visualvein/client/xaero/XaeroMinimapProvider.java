package com.visualvein.client.xaero;

import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;

import java.util.Iterator;
import java.util.List;

public final class XaeroMinimapProvider extends MinimapElementRenderProvider<XaeroVeinMarker, XaeroRenderContext> {

    private final List<XaeroVeinMarker> markers;
    private Iterator<XaeroVeinMarker> iterator;

    public XaeroMinimapProvider(List<XaeroVeinMarker> markers) {
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
    public XaeroVeinMarker getNext(MinimapElementRenderLocation location, XaeroRenderContext context) {
        return iterator.next();
    }

    @Override
    public void end(MinimapElementRenderLocation location, XaeroRenderContext context) {
        iterator = null;
    }
}
