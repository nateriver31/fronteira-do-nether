package br.com.survivalplus.core.portal;

import java.util.Objects;
import java.util.OptionalInt;

/** Pure portal math. These helpers never change Minecraft's coordinate_scale. */
public final class PortalGeometry {
    private PortalGeometry() {}

    public static double overworldToNether(double coordinate, double scale) {
        requireFinite(coordinate, "Coordenada");
        requirePositive(scale, "Escala");
        double result = coordinate / scale;
        requireFinite(result, "Coordenada convertida");
        return result;
    }

    public static double netherToOverworld(double coordinate, double scale) {
        requireFinite(coordinate, "Coordenada");
        requirePositive(scale, "Escala");
        double result = coordinate * scale;
        requireFinite(result, "Coordenada convertida");
        return result;
    }

    /** The effective ratio is Nether coordinate_scale / Overworld coordinate_scale. */
    public static double realRatio(double overworldScale, double netherScale) {
        requirePositive(overworldScale, "Escala do Overworld");
        requirePositive(netherScale, "Escala do Nether");
        double result = netherScale / overworldScale;
        requirePositive(result, "Proporção real");
        return result;
    }

    /** Full square, including allowance for a portal's width and the entity volume. */
    public static Bounds requiredSearchBounds(double targetX, double targetZ, int radius, double margin) {
        requireFinite(targetX, "X");
        requireFinite(targetZ, "Z");
        requireRadius(radius);
        Bounds.requireMargin(margin);
        double reach = radius + margin;
        return new Bounds(targetX - reach, targetX + reach, targetZ - reach, targetZ + reach);
    }

    /** Empty means even radius zero cannot fit with the required allowance. */
    public static OptionalInt maxSafeSearchRadius(Bounds allowed, double x, double z,
                                                 int configuredRadius, double margin) {
        Objects.requireNonNull(allowed, "allowed");
        requireRadius(configuredRadius);
        Bounds.requireMargin(margin);
        double available = allowed.distanceToEdge(x, z) - margin;
        if (available < 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) Math.min(configuredRadius, Math.floor(available)));
    }

    private static void requireRadius(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Raio não pode ser negativo.");
        }
    }

    private static void requirePositive(double number, String field) {
        requireFinite(number, field);
        if (number <= 0) {
            throw new IllegalArgumentException(field + " deve ser positiva.");
        }
    }

    private static void requireFinite(double number, String field) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(field + " deve ser finita.");
        }
    }
}
