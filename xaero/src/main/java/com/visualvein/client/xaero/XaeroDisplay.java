package com.visualvein.client.xaero;

/** Map display mode shared by the World Map and Minimap renderers. */
public final class XaeroDisplay {

    public enum Mode {
        ORE,
        FLUID,
        HIDDEN
    }

    private static Mode mode = Mode.ORE;
    private static boolean textures = true;

    private XaeroDisplay() {
    }

    public static Mode mode() {
        return mode;
    }

    public static void mode(Mode value) {
        mode = value;
    }

    public static boolean texture() {
        return textures;
    }

    public static void toggleTexture() {
        textures = !textures;
    }

    public static boolean showOre() {
        return mode == Mode.ORE;
    }

    public static boolean showFluid() {
        return mode == Mode.FLUID;
    }
}
