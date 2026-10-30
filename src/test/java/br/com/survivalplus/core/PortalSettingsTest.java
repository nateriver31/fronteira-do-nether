package br.com.survivalplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.survivalplus.core.portal.Bounds;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Exercises the packaged defaults and configuration validation without a running server. */
final class PortalSettingsTest {
    private static final String ROOT = "nether-portals.";

    private static YamlConfiguration defaults() throws Exception {
        InputStream input = PortalSettingsTest.class.getResourceAsStream("/config.yml");
        assertNotNull(input, "config.yml precisa estar empacotado nos resources");
        try (input; InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(reader);
            return configuration;
        }
    }

    private static void rejects(String path, Object value) throws Exception {
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + path, value);
        assertThrows(IllegalArgumentException.class, () -> PortalSettings.read(configuration), path);
    }

    @Test
    void packagedDefaultsMatchTheTwoCustomMapsAndScale() throws Exception {
        PortalSettings settings = PortalSettings.read(defaults());
        assertTrue(settings.enabled());
        assertEquals(6, settings.expectedScale());
        assertEquals("minecraft:overworld", settings.overworld().toString());
        assertEquals("minecraft:the_nether", settings.nether().toString());
        assertEquals(new Bounds(-7500, 7500, -6500, 6500), settings.overworldBounds());
        assertEquals(new Bounds(-4096, 4096, -4096, 4096), settings.netherBounds());
        assertEquals(new Bounds(-1200, 1200, -1000, 1000), settings.portalZone());
        assertEquals(16, settings.searchRadius());
        assertEquals(5_000_000_000L, settings.cooldownNanos());
        assertTrue(settings.titles());
        assertTrue(settings.actionbar());
        assertTrue(settings.sounds());
    }

    @Test
    void readingSettingsDoesNotModifyTheConfiguration() throws Exception {
        YamlConfiguration configuration = defaults();
        String before = configuration.saveToString();
        PortalSettings.read(configuration);
        assertEquals(before, configuration.saveToString());
    }

    @Test
    void settingsSnapshotDoesNotRetainMutableConfigurationValues() throws Exception {
        YamlConfiguration configuration = defaults();
        PortalSettings settings = PortalSettings.read(configuration);
        String outsideTitle = settings.messages().get("outside-title");
        configuration.set(ROOT + "messages.outside-title", "alterado");
        assertEquals(outsideTitle, settings.messages().get("outside-title"));
        assertThrows(UnsupportedOperationException.class, () -> settings.messages().put("outside-title", "alterado"));
    }

    @Test
    void customWorldKeysAreAcceptedWithoutAssumingWorldFolderNames() throws Exception {
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "dimensions.overworld", "survival:vraktus");
        configuration.set(ROOT + "dimensions.nether", "survival:nether_personalizado");
        PortalSettings settings = PortalSettings.read(configuration);
        assertEquals("survival:vraktus", settings.overworld().toString());
        assertEquals("survival:nether_personalizado", settings.nether().toString());
    }

    @Test
    void equalWorldKeysAreRejected() throws Exception {
        rejects("dimensions.nether", "minecraft:overworld");
    }

    @Test
    void malformedWorldAndSoundKeysAreRejected() throws Exception {
        for (String key : List.of("Minecraft:UpperCase", "minecraft:has spaces", "a:b:c")) {
            rejects("dimensions.nether", key);
            rejects("notifications.sounds-outside", key);
            rejects("notifications.sounds-inside", key);
        }
    }

    @Test
    void zeroNegativeNonfiniteAndHugeScalesAreRejected() throws Exception {
        for (double scale : new double[]{0, -6, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 0.0000001, 1000001}) {
            rejects("expected-scale", scale);
        }
    }

    @Test
    void numericStringsCannotSilentlyBecomeDefaultNumbers() throws Exception {
        rejects("expected-scale", "6");
        rejects("portal-zone.min-x", "-1200");
        rejects("search-radius", "16");
        rejects("notifications.cooldown-seconds", "5");
        rejects("notifications.sound-volume", "0.7");
    }

    @Test
    void expectedScaleIsAReadOnlyExpectationRatherThanAnInternalWorldScaleChange() throws Exception {
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "expected-scale", 5.5);
        PortalSettings settings = PortalSettings.read(configuration);
        assertEquals(5.5, settings.expectedScale());
        assertEquals(5.5, configuration.getDouble(ROOT + "expected-scale"));
        assertEquals(new Bounds(-1200, 1200, -1000, 1000), settings.portalZone());
    }

    @Test
    void equalOrReversedBoundsAreRejected() throws Exception {
        rejects("portal-zone.min-x", 1200);
        rejects("portal-zone.min-z", 1001);
        rejects("nether-bounds.max-x", -4096);
        rejects("overworld-bounds.max-z", -6501);
    }

    @Test
    void nonfiniteAndOutOfMinecraftRangeBoundsAreRejected() throws Exception {
        rejects("portal-zone.min-x", Double.NaN);
        rejects("nether-bounds.max-z", Double.POSITIVE_INFINITY);
        rejects("overworld-bounds.min-x", -30000000);
        rejects("overworld-bounds.max-z", 30000000);
    }

    @Test
    void portalZoneMustFitTheNetherOnEverySide() throws Exception {
        rejects("portal-zone.min-x", -4097);
        rejects("portal-zone.max-x", 4097);
        rejects("portal-zone.min-z", -4097);
        rejects("portal-zone.max-z", 4097);
    }

    @Test
    void scaledPortalZoneMustFitTheOverworldOnEverySide() throws Exception {
        // Each is inside the full Nether, but maps outside Vraktus at scale 6.
        rejects("portal-zone.min-x", -1300);
        rejects("portal-zone.max-x", 1300);
        rejects("portal-zone.min-z", -1100);
        rejects("portal-zone.max-z", 1100);
    }

    @Test
    void mappedZoneMayTouchPhysicalBoundsExactly() throws Exception {
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "overworld-bounds.min-x", -7200);
        configuration.set(ROOT + "overworld-bounds.max-x", 7200);
        configuration.set(ROOT + "overworld-bounds.min-z", -6000);
        configuration.set(ROOT + "overworld-bounds.max-z", 6000);
        assertEquals(new Bounds(-1200, 1200, -1000, 1000), PortalSettings.read(configuration).portalZone());
    }

    @Test
    void fractionalCoordinatesArePreservedRatherThanTruncated() throws Exception {
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "portal-zone.min-x", -1199.75);
        configuration.set(ROOT + "portal-zone.max-z", 999.25);
        PortalSettings settings = PortalSettings.read(configuration);
        assertEquals(-1199.75, settings.portalZone().minX());
        assertEquals(999.25, settings.portalZone().maxZ());
    }

    @Test
    void searchRadiusHasIntegerRangeAndRejectsNonfiniteValues() throws Exception {
        for (Object radius : new Object[]{-1, 129, 16.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            rejects("search-radius", radius);
        }
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "search-radius", 0);
        assertEquals(0, PortalSettings.read(configuration).searchRadius());
        configuration.set(ROOT + "search-radius", 128);
        assertEquals(128, PortalSettings.read(configuration).searchRadius());
    }

    @Test
    void cooldownHasIntegerRangeAndDoesNotOverflowAnInt() throws Exception {
        rejects("notifications.cooldown-seconds", -1);
        rejects("notifications.cooldown-seconds", 3601);
        rejects("notifications.cooldown-seconds", 1.5);
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "notifications.cooldown-seconds", 3600);
        assertEquals(3_600_000_000_000L, PortalSettings.read(configuration).cooldownNanos());
        configuration.set(ROOT + "notifications.cooldown-seconds", 0);
        assertEquals(0, PortalSettings.read(configuration).cooldownNanos());
    }

    @Test
    void stringOrNumericBooleansCannotSilentlyEnableOrDisableSafety() throws Exception {
        for (String path : List.of("enabled", "notifications.titles", "notifications.actionbar", "notifications.sounds")) {
            rejects(path, "true");
            rejects(path, 1);
            rejects(path, null);
        }
    }

    @Test
    void notificationTogglesAndExplicitDisableAreAccepted() throws Exception {
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "enabled", false);
        configuration.set(ROOT + "notifications.titles", false);
        configuration.set(ROOT + "notifications.actionbar", false);
        configuration.set(ROOT + "notifications.sounds", false);
        PortalSettings settings = PortalSettings.read(configuration);
        assertFalse(settings.enabled());
        assertFalse(settings.titles());
        assertFalse(settings.actionbar());
        assertFalse(settings.sounds());
    }

    @Test
    void soundsEnforceFiniteVolumeAndPitchRanges() throws Exception {
        for (double volume : new double[]{-0.01, 4.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            rejects("notifications.sound-volume", volume);
        }
        for (double pitch : new double[]{0, 0.009, 2.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            rejects("notifications.sound-pitch", pitch);
        }
    }

    @Test
    void allRequiredMessagesMustBeTextButMayBeEmptyToSuppressText() throws Exception {
        for (String id : List.of("outside-title", "outside-subtitle", "outside-chat", "inside-title",
                "inside-subtitle", "blocked-nether", "blocked-overworld", "unsafe", "ungenerated", "unavailable")) {
            rejects("messages." + id, null);
            rejects("messages." + id, 42);
        }
        YamlConfiguration configuration = defaults();
        configuration.set(ROOT + "messages.outside-chat", "");
        assertEquals("", PortalSettings.read(configuration).messages().get("outside-chat"));
    }

    @Test
    void messagesPreserveBothLegacyColorMarkersAndUnicode() throws Exception {
        YamlConfiguration configuration = defaults();
        String message = "&c⚠ §lPORTAL §r&6fora da zona";
        configuration.set(ROOT + "messages.blocked-nether", message);
        assertEquals(message, PortalSettings.read(configuration).messages().get("blocked-nether"));
    }

    @Test
    void absentOrMalformedRootCannotProduceUnrestrictedZeroBounds() {
        assertThrows(IllegalArgumentException.class, () -> PortalSettings.read(new YamlConfiguration()));
        YamlConfiguration malformed = new YamlConfiguration();
        malformed.set("nether-portals", List.of("not", "a", "section"));
        assertThrows(IllegalArgumentException.class, () -> PortalSettings.read(malformed));
    }
}
