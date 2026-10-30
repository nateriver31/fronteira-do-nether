package br.com.survivalplus.core.portal;

import java.util.OptionalInt;
import java.util.Random;

/** Dependency-free executable tests; throws on failures even without -ea. */
public final class PortalGeometryTest {
    private static int assertions;

    public static void main(String[] args) {
        conversions();
        bounds();
        safetyEnvelope();
        invalidInputs();
        System.out.println("PortalGeometryTest: " + assertions + " verificações passaram.");
    }

    private static void conversions() {
        double[][] examples = {
                {0, 0, 0, 0}, {500, 500, 3000, 3000},
                {1000, -500, 6000, -3000}, {-1000, 800, -6000, 4800},
                {-0.5, -0.25, -3, -1.5}
        };
        for (double[] example : examples) {
            eq(example[2], PortalGeometry.netherToOverworld(example[0], 6));
            eq(example[3], PortalGeometry.netherToOverworld(example[1], 6));
            eq(example[0], PortalGeometry.overworldToNether(example[2], 6));
            eq(example[1], PortalGeometry.overworldToNether(example[3], 6));
        }
        eq(-1.0 / 6, PortalGeometry.overworldToNether(-1, 6));
        eq(6, PortalGeometry.realRatio(1, 6));
        eq(6, PortalGeometry.realRatio(2, 12));
        eq(8, PortalGeometry.realRatio(1, 8));

        Bounds zone = new Bounds(-1200, 1200, -1000, 1000);
        yes(zone.contains(0, 0)); // Test 1
        yes(zone.contains(1000, 500)); // Test 2
        no(zone.contains(1300, 0)); // Test 3
        no(zone.contains(0, 1100)); // Test 4
        yes(zone.contains(PortalGeometry.overworldToNether(6000, 6),
                PortalGeometry.overworldToNether(3000, 6))); // Test 5
        no(zone.contains(PortalGeometry.overworldToNether(7800, 6), 0)); // Test 6
        no(zone.contains(PortalGeometry.overworldToNether(7400, 6), 0));
        no(zone.contains(PortalGeometry.overworldToNether(-7400, 6), 0));
        no(zone.contains(PortalGeometry.overworldToNether(7201, 6),
                PortalGeometry.overworldToNether(6001, 6)));
    }

    private static void bounds() {
        Bounds zone = new Bounds(-1200, 1200, -1000, 1000);
        for (double x : new double[] {-1200, 0, 1200}) {
            for (double z : new double[] {-1000, 0, 1000}) {
                yes(zone.contains(x, z));
            }
        }
        no(zone.contains(-1200.0001, 0));
        no(zone.contains(0, -1000.0001));
        no(zone.contains(1200.0001, 1000.0001));
        no(zone.contains(Double.NaN, 0));
        no(zone.contains(0, Double.POSITIVE_INFINITY));
        eq(1000, zone.distanceToEdge(0, 0));
        eq(0, zone.distanceToEdge(-1200, -1000));
        eq(-1, zone.distanceToEdge(1201, 0));
        yes(zone.contains(zone.inset(22)));
        eq(-1178, zone.inset(22).minX());
        eq(978, zone.inset(22).maxZ());
        yes(zone.equals(zone.inset(0)));
        Bounds physical = new Bounds(-4096, 4096, -4096, 4096);
        yes(zone.equals(zone.intersect(physical)));
        yes(new Bounds(0, 1200, -1000, 500).equals(zone.intersect(new Bounds(0, 2000, -2000, 500))));
    }

    private static void safetyEnvelope() {
        Bounds zone = new Bounds(-1200, 1200, -1000, 1000);
        eq(16, PortalGeometry.maxSafeSearchRadius(zone, 0, 0, 16, 22).orElseThrow());
        eq(8, PortalGeometry.maxSafeSearchRadius(zone, 1170, 0, 16, 22).orElseThrow());
        eq(0, PortalGeometry.maxSafeSearchRadius(zone, 1178, 978, 16, 22).orElseThrow());
        no(PortalGeometry.maxSafeSearchRadius(zone, 1178.001, 0, 16, 22).isPresent());
        no(PortalGeometry.maxSafeSearchRadius(zone, 0, 1000, 16, 22).isPresent());
        no(PortalGeometry.maxSafeSearchRadius(zone, 1300, 0, 16, 0).isPresent());
        eq(7, PortalGeometry.maxSafeSearchRadius(zone, 1170.5, 0, 16, 22).orElseThrow());
        eq(7, PortalGeometry.maxSafeSearchRadius(zone, -1170.5, 0, 16, 22).orElseThrow());
        Bounds search = PortalGeometry.requiredSearchBounds(-1170, 970, 8, 22);
        yes(zone.contains(search));
        eq(-1200, search.minX());
        eq(1000, search.maxZ());
        no(zone.contains(PortalGeometry.requiredSearchBounds(-1170, 970, 9, 22)));

        // If a search is admitted, every point of its square fits, including corners.
        Random random = new Random(6);
        for (int i = 0; i < 1000; i++) {
            double x = random.nextDouble() * 2600 - 1300;
            double z = random.nextDouble() * 2200 - 1100;
            OptionalInt radius = PortalGeometry.maxSafeSearchRadius(zone, x, z, 16, 22);
            if (radius.isPresent()) {
                yes(zone.contains(PortalGeometry.requiredSearchBounds(x, z, radius.getAsInt(), 22)));
            } else {
                yes(zone.distanceToEdge(x, z) < 22);
            }
        }
    }

    private static void invalidInputs() {
        invalid(() -> new Bounds(0, 0, -1, 1));
        invalid(() -> new Bounds(1, 0, -1, 1));
        invalid(() -> new Bounds(-1, 1, 0, 0));
        invalid(() -> new Bounds(Double.NaN, 1, -1, 1));
        invalid(() -> new Bounds(-1, Double.POSITIVE_INFINITY, -1, 1));
        Bounds square = new Bounds(-1, 1, -1, 1);
        invalid(() -> square.inset(1));
        invalid(() -> square.inset(-1));
        invalid(() -> square.inset(Double.NaN));
        invalid(() -> square.distanceToEdge(Double.NaN, 0));
        invalid(() -> square.intersect(new Bounds(2, 3, 2, 3)));
        invalid(() -> square.intersect(new Bounds(1, 2, 0, 1)));
        for (double scale : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            invalid(() -> PortalGeometry.overworldToNether(1, scale));
            invalid(() -> PortalGeometry.netherToOverworld(1, scale));
            invalid(() -> PortalGeometry.realRatio(scale, 6));
            invalid(() -> PortalGeometry.realRatio(1, scale));
        }
        invalid(() -> PortalGeometry.netherToOverworld(Double.MAX_VALUE, 6));
        invalid(() -> PortalGeometry.netherToOverworld(Double.NaN, 6));
        invalid(() -> PortalGeometry.overworldToNether(1, Double.MIN_VALUE));
        invalid(() -> PortalGeometry.realRatio(Double.MIN_VALUE, Double.MAX_VALUE));
        invalid(() -> PortalGeometry.requiredSearchBounds(0, 0, -1, 22));
        invalid(() -> PortalGeometry.requiredSearchBounds(Double.NaN, 0, 16, 22));
        invalid(() -> PortalGeometry.requiredSearchBounds(0, 0, 16, -1));
        invalid(() -> PortalGeometry.requiredSearchBounds(0, 0, 0, 0));
        invalid(() -> PortalGeometry.maxSafeSearchRadius(square, 0, 0, -1, 0));
        invalid(() -> PortalGeometry.maxSafeSearchRadius(square, 0, 0, 1, Double.NaN));
        invalid(() -> PortalGeometry.maxSafeSearchRadius(square, Double.POSITIVE_INFINITY, 0, 1, 0));
    }

    private static void eq(double expected, double actual) {
        assertions++;
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError("Esperado " + expected + ", obtido " + actual);
        }
    }

    private static void yes(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Condição esperada verdadeira");
    }

    private static void no(boolean condition) { yes(!condition); }

    private static void invalid(Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Esperava IllegalArgumentException");
    }
}
