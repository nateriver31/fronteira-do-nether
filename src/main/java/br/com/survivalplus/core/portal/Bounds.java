package br.com.survivalplus.core.portal;

import java.util.Locale;

/** Immutable, inclusive horizontal bounds. No world or chunk operations. */
public record Bounds(double minX, double maxX, double minZ, double maxZ) {
    public Bounds {
        if (!Double.isFinite(minX) || !Double.isFinite(maxX)
                || !Double.isFinite(minZ) || !Double.isFinite(maxZ)
                || minX >= maxX || minZ >= maxZ) {
            throw new IllegalArgumentException("Limites devem ser finitos, com mínimo menor que máximo.");
        }
    }

    public boolean contains(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(Bounds other) {
        return contains(other.minX, other.minZ) && contains(other.maxX, other.maxZ);
    }

    public Bounds intersect(Bounds other) {
        return new Bounds(Math.max(minX, other.minX), Math.min(maxX, other.maxX),
                Math.max(minZ, other.minZ), Math.min(maxZ, other.maxZ));
    }

    public Bounds inset(double margin) {
        requireMargin(margin);
        return new Bounds(minX + margin, maxX - margin, minZ + margin, maxZ - margin);
    }

    /** Negative when outside; zero on an edge; positive inside. */
    public double distanceToEdge(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordenadas devem ser finitas.");
        }
        return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
    }

    static void requireMargin(double margin) {
        if (!Double.isFinite(margin) || margin < 0) {
            throw new IllegalArgumentException("Margem deve ser finita e não negativa.");
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "X=[%.2f, %.2f], Z=[%.2f, %.2f]", minX, maxX, minZ, maxZ);
    }
}
