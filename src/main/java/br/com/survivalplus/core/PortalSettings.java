package br.com.survivalplus.core;

import br.com.survivalplus.core.portal.Bounds;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

/** Validated snapshot; malformed reloads must not silently produce zero limits. */
public record PortalSettings(boolean enabled, double expectedScale, NamespacedKey overworld,
        NamespacedKey nether, Bounds overworldBounds, Bounds netherBounds, Bounds portalZone,
        int searchRadius, boolean titles, boolean actionbar, boolean sounds, long cooldownNanos,
        String outsideSound, String insideSound, float volume, float pitch, Map<String, String> messages) {
    private static final String ROOT = "nether-portals.";

    /** Effective zone is always the projection of the entire configured Overworld. */
    public Bounds connectionZone() {
        return new Bounds(overworldBounds.minX() / expectedScale,
                overworldBounds.maxX() / expectedScale,
                overworldBounds.minZ() / expectedScale,
                overworldBounds.maxZ() / expectedScale).intersect(netherBounds);
    }

    public static PortalSettings read(FileConfiguration c) {
        double scale = number(c, "expected-scale", 0.000001, 1000000);
        NamespacedKey overworld = key(c, "dimensions.overworld");
        NamespacedKey nether = key(c, "dimensions.nether");
        if (overworld.equals(nether)) throw new IllegalArgumentException("Mundos devem ser distintos.");
        Bounds ow = bounds(c, "overworld-bounds");
        Bounds ne = bounds(c, "nether-bounds");
        Bounds zone = bounds(c, "portal-zone");
        if (!ne.contains(zone)) throw new IllegalArgumentException("portal-zone precisa caber no Nether.");
        // Validate the entire mapped zone, not just one corner or positive coordinates.
        Bounds mapped = new Bounds(zone.minX() * scale, zone.maxX() * scale,
                zone.minZ() * scale, zone.maxZ() * scale);
        if (!ow.contains(mapped)) throw new IllegalArgumentException("A zona × escala ultrapassa o Overworld.");
        Map<String, String> messages = new LinkedHashMap<>();
        for (String id : new String[]{"outside-title", "outside-subtitle", "outside-chat",
                "inside-title", "inside-subtitle", "blocked-nether", "blocked-overworld",
                "unsafe", "ungenerated", "unavailable"}) {
            messages.put(id, string(c, "messages." + id));
        }
        return new PortalSettings(bool(c, "enabled"), scale, overworld, nether, ow, ne, zone,
                integer(c, "search-radius", 0, 128), bool(c, "notifications.titles"),
                bool(c, "notifications.actionbar"), bool(c, "notifications.sounds"),
                integer(c, "notifications.cooldown-seconds", 0, 3600) * 1_000_000_000L,
                sound(c, "notifications.sounds-outside"), sound(c, "notifications.sounds-inside"),
                (float) number(c, "notifications.sound-volume", 0, 4),
                (float) number(c, "notifications.sound-pitch", 0.01, 2), Map.copyOf(messages));
    }

    private static Bounds bounds(FileConfiguration c, String path) {
        return new Bounds(number(c, path + ".min-x", -29999984, 29999984),
                number(c, path + ".max-x", -29999984, 29999984),
                number(c, path + ".min-z", -29999984, 29999984),
                number(c, path + ".max-z", -29999984, 29999984));
    }

    private static NamespacedKey key(FileConfiguration c, String path) {
        NamespacedKey key = NamespacedKey.fromString(string(c, path));
        if (key == null) throw new IllegalArgumentException(path + ": chave inválida.");
        return key;
    }

    private static String sound(FileConfiguration c, String path) {
        String s = string(c, path);
        if (NamespacedKey.fromString(s) == null) throw new IllegalArgumentException(path + ": som inválido.");
        return s;
    }

    private static String string(FileConfiguration c, String path) {
        Object value = c.get(ROOT + path);
        if (!(value instanceof String s)) throw new IllegalArgumentException(path + ": texto obrigatório.");
        return s;
    }

    private static boolean bool(FileConfiguration c, String path) {
        Object value = c.get(ROOT + path);
        if (!(value instanceof Boolean b)) throw new IllegalArgumentException(path + ": booleano obrigatório.");
        return b;
    }

    private static double number(FileConfiguration c, String path, double min, double max) {
        Object value = c.get(ROOT + path);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() < min || n.doubleValue() > max) {
            throw new IllegalArgumentException(path + ": número finito entre " + min + " e " + max + ".");
        }
        return n.doubleValue();
    }

    private static int integer(FileConfiguration c, String path, int min, int max) {
        double n = number(c, path, min, max);
        if (n != Math.rint(n)) throw new IllegalArgumentException(path + ": inteiro obrigatório.");
        return (int) n;
    }
}
