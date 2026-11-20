package com.visualprospecting.data;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GTCEu stores one independent vein per world-gen layer (stone, deepslate, netherrack, endstone) at the
 * same origin. Drawing all of them on one 16x16 square makes labels overlap and shows a deepslate vein
 * on the surface where the player sees a stone vein's surface rock. This groups them and selects the
 * layer appropriate for the current map view.
 */
public record VeinGroup(String key, List<VeinRecord> layers, VeinRecord primary) {

    public static List<VeinGroup> group(Collection<VeinRecord> records, boolean underground, Integer viewY) {
        Map<String, List<VeinRecord>> grouped = new LinkedHashMap<>();
        for (VeinRecord record : records) {
            grouped.computeIfAbsent(record.originX + ":" + record.originZ, ignored -> new ArrayList<>()).add(record);
        }
        List<VeinGroup> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<VeinRecord>> entry : grouped.entrySet()) {
            List<VeinRecord> layers = entry.getValue();
            layers.sort(Comparator.comparingInt(record -> priority(record.layer)));
            VeinRecord primary = select(layers, underground, viewY);
            result.add(new VeinGroup(entry.getKey(), layers, primary));
        }
        return result;
    }

    private static VeinRecord select(List<VeinRecord> layers, boolean underground, Integer viewY) {
        if (underground) {
            if (viewY != null) {
                VeinRecord best = layers.get(0);
                int bestDistance = Integer.MAX_VALUE;
                for (VeinRecord record : layers) {
                    int distance = Math.abs(record.centerY - viewY);
                    if ("deepslate".equals(record.layer)) {
                        distance -= 8;
                    }
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = record;
                    }
                }
                return best;
            }
            for (VeinRecord record : layers) {
                if ("deepslate".equals(record.layer)) {
                    return record;
                }
            }
        } else {
            for (VeinRecord record : layers) {
                if ("stone".equals(record.layer) || "netherrack".equals(record.layer)
                        || "endstone".equals(record.layer)) {
                    return record;
                }
            }
        }
        return layers.get(0);
    }

    private static int priority(String layer) {
        return switch (layer) {
            case "stone" -> 0;
            case "netherrack", "endstone" -> 1;
            case "deepslate" -> 2;
            default -> 3;
        };
    }

    public String title() {
        StringBuilder builder = new StringBuilder();
        builder.append(VeinNames.veinName(primary.veinId).getString()).append('\n');
        builder.append(Component.translatable("vp.tooltip.layer_y",
                VeinNames.layerName(primary.layer).getString(), primary.centerY).getString()).append('\n');
        builder.append(Component.translatable("vp.tooltip.center",
                primary.centerX, primary.centerZ).getString()).append('\n');
        builder.append(Component.translatable("vp.tooltip.size_density",
                primary.clusterSize, Math.round(primary.density * 100.0F)).getString()).append('\n');
        builder.append(oreLines(primary));
        List<VeinRecord> others = new ArrayList<>();
        for (VeinRecord layer : layers) {
            if (layer != primary) {
                others.add(layer);
            }
        }
        if (!others.isEmpty()) {
            builder.append(Component.translatable("vp.tooltip.other_layers").getString()).append('\n');
            for (VeinRecord other : others) {
                builder.append(Component.translatable("vp.tooltip.other_layer_entry",
                        VeinNames.veinName(other.veinId).getString(),
                        VeinNames.layerName(other.layer).getString(),
                        other.centerY).getString()).append('\n');
            }
        }
        if (!primary.surfaceOres.isEmpty()) {
            builder.append(Component.translatable("vp.tooltip.surface").getString());
            for (int i = 0; i < primary.surfaceOres.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(VeinNames.oreName(primary.surfaceOres.get(i)).getString());
            }
            builder.append('\n');
        }
        builder.append(Component.translatable("vp.tooltip.mined", primary.mined).getString()).append('\n');
        builder.append(Component.translatable("vp.tooltip.mark_status",
                Component.translatable(primary.marked ? "vp.tooltip.manual" : "vp.tooltip.auto").getString(),
                Component.translatable(primary.depleted ? "vp.tooltip.depleted" : "vp.tooltip.active").getString())
                .getString());
        return builder.toString().trim();
    }

    private static String oreLines(VeinRecord record) {
        if (record.ores.isEmpty()) {
            return Component.translatable("vp.tooltip.ores_unknown").getString() + "\n";
        }
        int total = 0;
        for (OreEntry ore : record.ores) {
            total += ore.weight;
        }
        StringBuilder builder = new StringBuilder(
                Component.translatable("vp.tooltip.ores").getString() + "\n");
        for (OreEntry ore : record.ores) {
            builder.append("  ").append(VeinNames.oreName(ore).getString());
            if (total > 0) {
                builder.append("  ").append(Math.round(ore.weight * 100.0F / total)).append('%');
            } else {
                builder.append("  -");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    public String label() {
        String name = VeinNames.veinName(primary.veinId).getString();
        if (primary.depleted) {
            name = Component.translatable("vp.label.depleted").getString() + " " + name;
        }
        return name + "\n" + VeinNames.layerName(primary.layer).getString();
    }

    public Component compactName() {
        return VeinNames.veinName(primary.veinId).copy()
                .append(Component.literal(" (").append(VeinNames.layerName(primary.layer)).append(")"));
    }
}
