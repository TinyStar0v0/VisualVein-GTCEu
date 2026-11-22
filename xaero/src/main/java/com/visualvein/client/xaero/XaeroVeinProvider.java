package com.visualvein.client.xaero;

import xaero.map.element.MapElementRenderProvider;
import xaero.map.element.render.ElementRenderLocation;

import java.util.Iterator;
import java.util.List;

public final class XaeroVeinProvider extends MapElementRenderProvider<XaeroVeinMarker, XaeroRenderContext> {

    private final List<XaeroVeinMarker> markers;
    private Iterator<XaeroVeinMarker> iterator;

    public XaeroVeinProvider(List<XaeroVeinMarker> markers) {
        this.markers = markers;
    }

    @Override
    public void begin(ElementRenderLocation location, XaeroRenderContext context) {
        iterator = markers.iterator();
    }

    @Override
    public boolean hasNext(ElementRenderLocation location, XaeroRenderContext context) {
        return iterator != null && iterator.hasNext();
    }

    @Override
    public XaeroVeinMarker getNext(ElementRenderLocation location, XaeroRenderContext context) {
        return iterator.next();
    }

    @Override
    public void end(ElementRenderLocation location, XaeroRenderContext context) {
        iterator = null;
    }

    @Override
    @Deprecated
    public void begin(int location, XaeroRenderContext context) {
        begin(ElementRenderLocation.fromIndex(location), context);
    }

    @Override
    @Deprecated
    public boolean hasNext(int location, XaeroRenderContext context) {
        return hasNext(ElementRenderLocation.fromIndex(location), context);
    }

    @Override
    @Deprecated
    public XaeroVeinMarker getNext(int location, XaeroRenderContext context) {
        return getNext(ElementRenderLocation.fromIndex(location), context);
    }

    @Override
    @Deprecated
    public void end(int location, XaeroRenderContext context) {
        end(ElementRenderLocation.fromIndex(location), context);
    }
}
