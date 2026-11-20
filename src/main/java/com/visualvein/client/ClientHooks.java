package com.visualvein.client;

import java.util.function.BooleanSupplier;

/**
 * Hooks filled in by the JourneyMap plugins. Keeping this class free of JourneyMap imports lets the
 * rest of the mod load safely on servers and on clients without JourneyMap.
 */
public final class ClientHooks {

    public static Runnable toggleOverlay = () -> {
    };
    public static Runnable createWaypoint = () -> {
    };
    public static Runnable dataChanged = () -> {
    };
    public static Runnable openMenu = () -> {
    };
    public static Runnable scanNearby = () -> {
    };
    public static Runnable toggleLabels = () -> {
    };
    public static Runnable toggleMinimap = () -> {
    };
    public static Runnable toggleFluid = () -> {
    };
    public static Runnable toggleHoveredStatus = () -> {
    };
    public static Runnable toggleMarkerStyle = () -> {
    };
    public static BooleanSupplier overlayEnabled = () -> true;
    public static BooleanSupplier labelsEnabled = () -> true;
    public static BooleanSupplier minimapEnabled = () -> false;
    public static BooleanSupplier fluidEnabled = () -> true;
    public static BooleanSupplier textureMarkers = () -> true;

    private ClientHooks() {
    }
}
