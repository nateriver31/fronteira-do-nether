package br.com.survivalplus.core;

import br.com.survivalplus.core.portal.Bounds;
import br.com.survivalplus.core.portal.PortalGeometry;
import java.util.Objects;
import java.util.OptionalInt;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

/** All operations run on the server thread. No blocks are set and no teleports are issued. */
final class PortalGuard {
    record Status(World overworld, World nether, Double overworldScale, Double netherScale,
                  Double ratio, String problem) {
        boolean ready() { return problem == null; }
    }
    record Decision(boolean allowed, int radius, String message) {
        static Decision deny(String message) { return new Decision(false, 0, message); }
        static Decision allow(int radius) { return new Decision(true, radius, null); }
    }

    private final SurvivalPlusCore plugin;
    private final ScaleReader scales = new ScaleReader();
    private PortalSettings settings;
    private String configurationFault;
    private String lastReported;

    PortalGuard(SurvivalPlusCore plugin, PortalSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    PortalSettings settings() { return settings; }
    boolean active() { return configurationFault != null || settings.enabled(); }

    void configure(PortalSettings settings) {
        this.settings = settings;
        this.configurationFault = null;
    }

    void fault(String message) { configurationFault = message; }

    Status status() {
        World ow = Bukkit.getWorlds().stream().filter(w -> w.getKey().equals(settings.overworld())).findFirst().orElse(null);
        World ne = Bukkit.getWorlds().stream().filter(w -> w.getKey().equals(settings.nether())).findFirst().orElse(null);
        if (configurationFault != null) return report(new Status(ow, ne, null, null, null, "Configuração inválida: " + configurationFault));
        if (ow == null || ne == null) return report(new Status(ow, ne, null, null, null, "Mundo configurado não está carregado; confira as chaves em status."));
        if (ow.getEnvironment() != World.Environment.NORMAL || ne.getEnvironment() != World.Environment.NETHER) {
            return report(new Status(ow, ne, null, null, null, "Ambientes dos mundos não correspondem a NORMAL/NETHER."));
        }
        String version = Bukkit.getMinecraftVersion();
        if (!(version.equals("26.2") || version.startsWith("26.2.")) || Runtime.version().feature() < 25) {
            return report(new Status(ow, ne, null, null, null, "Alvo exigido: Minecraft 26.2 e Java 25 ou superior. Detectado: " + version + "/Java " + Runtime.version().feature()));
        }
        try {
            double os = scales.read(ow);
            double ns = scales.read(ne);
            double ratio = PortalGeometry.realRatio(os, ns);
            String problem = Math.abs(ratio - settings.expectedScale()) <= 1e-9 * Math.max(1, settings.expectedScale())
                    ? null : "Escala real 1:" + ratio + " difere da esperada 1:" + settings.expectedScale() + ". Confira os dimension_type e reinicie; reload do plugin não muda a escala.";
            return report(new Status(ow, ne, os, ns, ratio, problem));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return report(new Status(ow, ne, null, null, null, "Não foi possível ler coordinate_scale pela API pública: " + e.getClass().getSimpleName() + ". Portais bloqueados."));
        }
    }

    private Status report(Status status) {
        String state = !active() ? "Sistema desativado na configuração; controle de portais não aplicado."
                : status.problem() == null ? "Escala real validada; regras de portais ativas." : status.problem();
        if (!Objects.equals(state, lastReported)) {
            if (active() && !status.ready()) plugin.getLogger().severe(state);
            else plugin.getLogger().info(state);
            lastReported = state;
        }
        return status;
    }

    boolean touchesManagedWorld(Location at) {
        return at != null && at.getWorld() != null
                && (at.getWorld().getKey().equals(settings.overworld()) || at.getWorld().getKey().equals(settings.nether()));
    }

    boolean isNether(Location at) {
        return at != null && at.getWorld() != null && at.getWorld().getKey().equals(settings.nether());
    }

    /** EntityPortalEvent also represents End portals: those are always outside our scope. */
    boolean entityPortalInScope(Location from, Location to) {
        if (isEnd(from) || isEnd(to)) return false;
        boolean netherTransit = environment(from, World.Environment.NETHER) || environment(to, World.Environment.NETHER)
                || isNether(from) || isNether(to);
        return netherTransit && (touchesManagedWorld(from) || touchesManagedWorld(to));
    }

    private static boolean isEnd(Location at) { return environment(at, World.Environment.THE_END); }
    private static boolean environment(Location at, World.Environment e) {
        return at != null && at.getWorld() != null && at.getWorld().getEnvironment() == e;
    }

    /** Identity and source policy, independent of any vanilla clamp/search on the initial target. */
    private Decision origin(Location from, World target, Status status) {
        if (!status.ready()) return Decision.deny("unavailable");
        if (from == null || from.getWorld() == null || target == null) return Decision.deny("unsafe");
        boolean out = from.getWorld().equals(status.nether());
        if (out ? !target.equals(status.overworld()) : !from.getWorld().equals(status.overworld()) || !target.equals(status.nether())) {
            return Decision.deny("unsafe");
        }
        if (!finite(from)) return Decision.deny("unsafe");
        try {
            // Bounds first also prevent arithmetic overflow on a malformed event.
            if (out && !settings.connectionZone().contains(from.getX(), from.getZ())) return Decision.deny("blocked-nether");
            if (!allowed(from.getWorld()).contains(from.getX(), from.getZ())) return Decision.deny("unsafe");
            double x = out ? PortalGeometry.netherToOverworld(from.getX(), status.ratio())
                    : PortalGeometry.overworldToNether(from.getX(), status.ratio());
            double z = out ? PortalGeometry.netherToOverworld(from.getZ(), status.ratio())
                    : PortalGeometry.overworldToNether(from.getZ(), status.ratio());
            if (!out && !settings.connectionZone().contains(x, z)) return Decision.deny("blocked-overworld");
            if (!allowed(target).contains(x, z)) return Decision.deny("unsafe");
        } catch (IllegalArgumentException e) { return Decision.deny("unsafe"); }
        return Decision.allow(0);
    }

    Decision initial(Entity entity, Location from, Location to, int requestedRadius) {
        Status state = status();
        Decision origin = origin(from, to == null ? null : to.getWorld(), state);
        if (!origin.allowed()) return origin;
        if (!finite(to)) return Decision.deny("unsafe");
        try {
            boolean out = isNether(from);
            double expectedX = out ? from.getX() * state.ratio() : from.getX() / state.ratio();
            double expectedZ = out ? from.getZ() * state.ratio() : from.getZ() / state.ratio();
            // Initial vanilla destination may be floored. A remote target changed
            // by another plugin is not a valid application of the expected scale.
            if (Math.abs(to.getX() - expectedX) > 1.01 || Math.abs(to.getZ() - expectedZ) > 1.01) return Decision.deny("unsafe");
            // Search need not fit inside the connection zone: the actual selected
            // exit is checked by finalExit before any dimensional transition.
            // Reserve entity volume at physical borders, not a blanket 24-block
            // exclusion strip that incorrectly shrinks the usable Overworld.
            double half = horizontalHalfExtent(entity);
            Bounds area = physical(to.getWorld());
            OptionalInt radius = PortalGeometry.maxSafeSearchRadius(area, to.getX(), to.getZ(),
                    Math.min(settings.searchRadius(), Math.max(0, requestedRadius)), half);
            if (radius.isEmpty()) return Decision.deny("unsafe");
            Bounds search = PortalGeometry.requiredSearchBounds(to.getX(), to.getZ(), radius.getAsInt(), 24 + half)
                    .intersect(area);
            if (!previouslyGenerated(to.getWorld(), search)) return Decision.deny("ungenerated");
            return Decision.allow(radius.getAsInt());
        } catch (IllegalArgumentException e) { return Decision.deny("unsafe"); }
    }

    Decision finalExit(Entity entity, Location originalFrom, Location to) {
        Status state = status();
        Decision origin = origin(originalFrom, to == null ? null : to.getWorld(), state);
        if (!origin.allowed()) return origin;
        if (!finite(to)) return Decision.deny("unsafe");
        try {
            double half = horizontalHalfExtent(entity);
            Bounds destination = new Bounds(to.getX() - half, to.getX() + half, to.getZ() - half, to.getZ() + half);
            double ratio = state.ratio();
            double expectedX = isNether(originalFrom) ? originalFrom.getX() * ratio : originalFrom.getX() / ratio;
            double expectedZ = isNether(originalFrom) ? originalFrom.getZ() * ratio : originalFrom.getZ() / ratio;
            double reach = settings.searchRadius() + 24 + half;
            if (Math.abs(to.getX() - expectedX) > reach || Math.abs(to.getZ() - expectedZ) > reach) return Decision.deny("unsafe");
            return allowed(to.getWorld()).contains(destination) ? Decision.allow(0) : Decision.deny("unsafe");
        } catch (IllegalArgumentException e) { return Decision.deny("unsafe"); }
    }

    private Bounds allowed(World world) {
        Bounds area = physical(world);
        return world.getKey().equals(settings.nether()) ? area.intersect(settings.connectionZone()) : area;
    }

    private Bounds physical(World world) {
        Bounds configured;
        if (world.getKey().equals(settings.nether())) configured = settings.netherBounds();
        else if (world.getKey().equals(settings.overworld())) configured = settings.overworldBounds();
        else throw new IllegalArgumentException("Mundo não configurado.");
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() / 2;
        return configured.intersect(new Bounds(center.getX() - halfSize, center.getX() + halfSize,
                center.getZ() - halfSize, center.getZ() + halfSize));
    }

    private static double horizontalHalfExtent(Entity entity) {
        BoundingBox box = entity.getBoundingBox();
        double half = Math.max(box.getWidthX(), box.getWidthZ()) / 2 + 0.01;
        if (!Double.isFinite(half) || half <= 0 || half > 128) throw new IllegalArgumentException("Volume de entidade inválido.");
        return half;
    }

    private static boolean finite(Location at) {
        return at != null && Double.isFinite(at.getX()) && Double.isFinite(at.getY()) && Double.isFinite(at.getZ());
    }

    private static boolean previouslyGenerated(World world, Bounds area) {
        int minX = ((int) Math.floor(area.minX())) >> 4;
        int maxX = ((int) Math.floor(area.maxX())) >> 4;
        int minZ = ((int) Math.floor(area.minZ())) >> 4;
        int maxZ = ((int) Math.floor(area.maxZ())) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!world.isChunkGenerated(x, z)) return false;
        }
        return true;
    }

    Decision position(Location at) {
        Status state = status();
        if (!state.ready()) return Decision.deny("unavailable");
        World target = isNether(at) ? state.overworld() : state.nether();
        return origin(at, target, state);
    }
}
