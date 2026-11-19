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
        builder.append("层级: ").append(VeinNames.layerName(primary.layer).getString())
                .append("   中心 Y: ").append(primary.centerY).append('\n');
        builder.append("中心: ").append(primary.centerX).append(", ").append(primary.centerZ).append('\n');
        builder.append("尺寸参数: ").append(primary.clusterSize)
                .append("   密度: ").append(Math.round(primary.density * 100.0F)).append("%\n");
        builder.append(oreLines(primary));
        List<VeinRecord> others = new ArrayList<>();
        for (VeinRecord layer : layers) {
            if (layer != primary) {
                others.add(layer);
            }
        }
        if (!others.isEmpty()) {
            builder.append("同原点其他层:\n");
            for (VeinRecord other : others) {
                builder.append("  ").append(VeinNames.veinName(other.veinId).getString())
                        .append(" / ").append(VeinNames.layerName(other.layer).getString())
                        .append(" (Y ").append(other.centerY).append(")\n");
            }
        }
        if (!primary.surfaceOres.isEmpty()) {
            builder.append("地表矿: ");
            for (int i = 0; i < primary.surfaceOres.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(VeinNames.oreName(primary.surfaceOres.get(i)).getString());
            }
            builder.append('\n');
        }
        builder.append("已开采（近似）: ").append(primary.mined).append('\n');
        builder.append("标记: ").append(primary.marked ? "手动" : "自动")
                .append("   状态: ").append(primary.depleted ? "已枯竭" : "未枯竭");
        return builder.toString().trim();
    }

    private static String oreLines(VeinRecord record) {
        if (record.ores.isEmpty()) {
            return "矿石组成: 未知\n";
        }
        int total = 0;
        for (OreEntry ore : record.ores) {
            total += ore.weight;
        }
        StringBuilder builder = new StringBuilder("矿石组成（权重）:\n");
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
            name += " [枯竭]";
        }
        return name + "\n" + VeinNames.layerName(primary.layer).getString();
    }

    public Component compactName() {
        return VeinNames.veinName(primary.veinId).copy()
                .append(Component.literal(" (").append(VeinNames.layerName(primary.layer)).append(")"));
    }
}
