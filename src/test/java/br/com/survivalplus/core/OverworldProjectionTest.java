package br.com.survivalplus.core;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class OverworldProjectionTest {
    private PortalSettings settings() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            var c = new YamlConfiguration();
            c.load(reader);
            return PortalSettings.read(c);
        }
    }

    @Test void screenshotPositionIsAuthorizedDespiteLegacyZone() throws Exception {
        var s = settings();
        assertTrue(s.connectionZone().contains(6499.724 / 6, 6498.256 / 6));
        assertTrue(s.connectionZone().contains(-6499.724 / 6, -6498.256 / 6));
    }

    @Test void allOverworldEdgesProjectInsideAndOutsideNetherRemainsBlocked() throws Exception {
        var s = settings();
        var zone = s.connectionZone();
        for (double x : new double[]{-7500, 0, 7500})
            for (double z : new double[]{-6500, 0, 6500})
                assertTrue(zone.contains(x / 6, z / 6));
        assertEquals(1250, zone.maxX());
        assertEquals(6500.0 / 6, zone.maxZ());
        assertFalse(zone.contains(1250.01, 0));
        assertFalse(zone.contains(0, 6500.0 / 6 + 0.01));
        assertFalse(zone.contains(-1250.01, 0));
        assertFalse(zone.contains(0, -6500.0 / 6 - 0.01));
        assertTrue(zone.contains(7400.0 / 6, 0));
    }
}
