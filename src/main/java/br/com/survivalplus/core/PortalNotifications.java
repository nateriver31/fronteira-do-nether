package br.com.survivalplus.core;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class PortalNotifications {
    private final SurvivalPlusCore plugin;
    private final Map<UUID, Boolean> inside = new HashMap<>();
    private final Map<UUID, Long> lastAttempt = new HashMap<>();

    PortalNotifications(SurvivalPlusCore plugin) { this.plugin = plugin; }

    static Component text(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s.replace('§', '&'));
    }

    void refresh(Player player, Location at) {
        PortalSettings s = plugin.guard().settings();
        if (!s.enabled() || at.getWorld() == null || !at.getWorld().getKey().equals(s.nether())) {
            inside.remove(player.getUniqueId());
            return;
        }
        boolean now = s.connectionZone().contains(at.getX(), at.getZ());
        Boolean previous = inside.put(player.getUniqueId(), now);
        if (previous == null && now || previous != null && previous == now) return;
        String prefix = now ? "inside" : "outside";
        if (s.titles()) {
            player.showTitle(Title.title(text(s.messages().get(prefix + "-title")),
                    text(s.messages().get(prefix + "-subtitle")),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
        if (!now && !s.messages().get("outside-chat").isEmpty()) player.sendMessage(text(s.messages().get("outside-chat")));
        if (s.sounds()) player.playSound(at, now ? s.insideSound() : s.outsideSound(), s.volume(), s.pitch());
    }

    void blocked(Player player, String message) {
        PortalSettings s = plugin.guard().settings();
        if (!s.actionbar()) return;
        long now = System.nanoTime();
        Long before = lastAttempt.get(player.getUniqueId());
        if (before != null && now - before < s.cooldownNanos()) return;
        lastAttempt.put(player.getUniqueId(), now);
        player.sendActionBar(text(s.messages().getOrDefault(message, s.messages().get("unsafe"))));
    }

    void forget(UUID id) { inside.remove(id); lastAttempt.remove(id); }
    void clear() { inside.clear(); lastAttempt.clear(); }
}
