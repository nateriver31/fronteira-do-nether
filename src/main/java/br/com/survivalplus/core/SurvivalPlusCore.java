package br.com.survivalplus.core;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SurvivalPlusCore extends JavaPlugin {
    private PortalGuard guard;
    private PortalNotifications notifications;
    private PortalListener listener;

    @Override public void onEnable() {
        saveDefaultConfig();
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            // Defaults identify the protected pair even when the user's YAML is malformed.
            guard = new PortalGuard(this, PortalSettings.read(YamlConfiguration.loadConfiguration(reader)));
        } catch (Exception e) {
            throw new IllegalStateException("Configuração interna do plugin inválida.", e);
        }
        notifications = new PortalNotifications(this);
        listener = new PortalListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this);
        loadSettings();
        Bukkit.getScheduler().runTask(this, () -> {
            guard.status();
            Bukkit.getOnlinePlayers().forEach(p -> notifications.refresh(p, p.getLocation()));
        });
    }

    @Override public void onDisable() {
        if (listener != null) listener.clear();
        if (notifications != null) notifications.clear();
        Bukkit.getScheduler().cancelTasks(this);
    }

    PortalGuard guard() { return guard; }
    PortalNotifications notifications() { return notifications; }

    private boolean loadSettings() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new File(getDataFolder(), "config.yml"));
            guard.configure(PortalSettings.read(yaml));
            return true;
        } catch (Exception e) {
            // Retain previous keys/rules and lock the system instead of reverting to vanilla.
            guard.fault(e.getMessage());
            getLogger().severe("Configuração recusada; portais protegidos permanecerão bloqueados: " + e.getMessage());
            return false;
        } finally {
            listener.clear();
            guard.status();
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("survivalplus.admin")) {
            say(sender, "&cVocê não tem permissão.");
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("nether")) {
            say(sender, "&eUso: /survivalplus nether <status|reload|debug>");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "reload" -> {
                boolean loaded = loadSettings();
                say(sender, loaded ? "&aConfiguração recarregada. A escala interna NÃO foi alterada."
                        : "&cConfiguração inválida; viagens protegidas bloqueadas. Confira o console.");
                Bukkit.getOnlinePlayers().forEach(p -> notifications.refresh(p, p.getLocation()));
                status(sender);
            }
            case "debug" -> debug(sender);
            default -> say(sender, "&eUso: /survivalplus nether <status|reload|debug>");
        }
        return true;
    }

    private void status(CommandSender sender) {
        PortalGuard.Status s = guard.status();
        PortalSettings c = guard.settings();
        say(sender, "&6SurvivalPlusCore — " + (!guard.active() ? "DESATIVADO" : s.ready() ? "ATIVO" : "BLOQUEADO"));
        say(sender, "&eEsperada: 1:" + c.expectedScale() + "; real: " + (s.ratio() == null ? "indisponível" : "1:" + s.ratio()));
        say(sender, "&ecoordinate_scale: Overworld=" + s.overworldScale() + "; Nether=" + s.netherScale());
        say(sender, "&eMundos: " + c.overworld() + " ↔ " + c.nether());
        say(sender, "&eZona Nether: " + c.portalZone());
        say(sender, "&eLimites OW: " + c.overworldBounds() + "; Nether: " + c.netherBounds());
        say(sender, "&eRaio máximo: " + c.searchRadius() + "; criação automática: bloqueada.");
        if (s.problem() != null) say(sender, "&cMotivo: " + s.problem());
        say(sender, "&7Mundos carregados: " + String.join(", ", Bukkit.getWorlds().stream().map(w -> w.getKey().toString()).toList()));
    }

    private void debug(CommandSender sender) {
        if (!(sender instanceof Player player)) { say(sender, "&cDebug de posição precisa ser executado por um jogador."); return; }
        Location at = player.getLocation();
        say(sender, String.format(Locale.ROOT, "&eDimensão: %s; X=%.3f Y=%.3f Z=%.3f", at.getWorld().getKey(), at.getX(), at.getY(), at.getZ()));
        if (!guard.touchesManagedWorld(at)) { say(sender, "&7Este mundo não é controlado pelo sistema."); return; }
        if (!guard.active()) { say(sender, "&7Controle desativado; regras não aplicadas."); return; }
        PortalGuard.Decision d = guard.position(at);
        say(sender, d.allowed() ? "&aPosição autorizada. A viagem ainda depende de saída existente, margem e chunks gerados."
                : guard.settings().messages().get(d.message()));
        if (guard.isNether(at)) say(sender, String.format(Locale.ROOT, "&7Correspondência esperada OW: X=%.3f Z=%.3f", at.getX() * guard.settings().expectedScale(), at.getZ() * guard.settings().expectedScale()));
        else say(sender, String.format(Locale.ROOT, "&7Correspondência esperada Nether: X=%.3f Z=%.3f", at.getX() / guard.settings().expectedScale(), at.getZ() / guard.settings().expectedScale()));
    }

    private static void say(CommandSender sender, String message) { sender.sendMessage(PortalNotifications.text(message)); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("survivalplus.admin")) return List.of();
        if (args.length == 1) return List.of("nether").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("nether")) return List.of("status", "reload", "debug").stream().filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
