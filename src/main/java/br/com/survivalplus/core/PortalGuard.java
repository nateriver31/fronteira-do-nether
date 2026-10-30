package br.com.survivalplus.core;

import br.com.survivalplus.core.portal.Bounds;
import br.com.survivalplus.core.portal.PortalGeometry;
import java.util.Objects;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Entity;
import org.bukkit.block.BlockState;
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

    private final Logger logger;
    private final Supplier<Status> statusProvider;
    private final ScaleReader scales = new ScaleReader();
    private PortalSettings settings;
    private String configurationFault;
    private String lastReported;

    PortalGuard(SurvivalPlusCore plugin, PortalSettings settings) {
        this(plugin.getLogger(), settings, null);
    }

    PortalGuard(Logger logger, PortalSettings settings, Supplier<Status> statusProvider) {
        this.logger = logger;
        this.settings = settings;
        this.statusProvider = statusProvider;
    }

    PortalSettings settings() { return settings; }
    boolean active() { return configurationFault != null || settings.enabled(); }

    void configure(PortalSettings settings) {
        this.settings = settings;
        this.configurationFault = null;
    }

    void fault(String message) { configurationFault = message; }

    Status status() {
        if (statusProvider != null) return statusProvider.get();
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
            if (active() && !status.ready()) logger.severe(state);
            else logger.info(state);
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
            // Vanilla may load/generate the local destination chunks as part of
            // this trip. No whole-region pre-generation or prebuilt exit required.
            // The selected exit and every proposed creation block are guarded later.
            return Decision.allow(Math.min(searchRadius(to.getWorld()), Math.max(0, requestedRadius)));
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
            double reach = searchRadius(to.getWorld()) + 24 + half;
            if (Math.abs(to.getX() - expectedX) > reach || Math.abs(to.getZ() - expectedZ) > reach) return Decision.deny("unsafe");
            return allowed(to.getWorld()).contains(destination) ? Decision.allow(0) : Decision.deny("unsafe");
        } catch (IllegalArgumentException e) { return Decision.deny("unsafe"); }
    }

    int searchRadius(World destination) {
        // Equivalent distances in both dimensions help a locally accommodated
        // Nether exit find the original Overworld portal on the return trip.
        double radius = settings.searchRadius() *
                (destination.getKey().equals(settings.overworld()) ? settings.expectedScale() : 1);
        return (int) Math.min(128, Math.ceil(radius));
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

    /** Accommodate a vanilla exit locally, without changing the world's scale. */
    Location creationTarget(Entity entity, Location to) {
        double margin = 4 + horizontalHalfExtent(entity);
        Bounds inner = allowed(to.getWorld()).inset(margin);
        Location adjusted = to.clone();
        adjusted.setX(Math.max(inner.minX(), Math.min(inner.maxX(), to.getX())));
        adjusted.setZ(Math.max(inner.minZ(), Math.min(inner.maxZ(), to.getZ())));
        return adjusted;
    }

    int creationRadius(Entity entity, Location adjustedTarget, int requestedRadius) {
        double remaining = allowed(adjustedTarget.getWorld()).distanceToEdge(adjustedTarget.getX(), adjustedTarget.getZ())
                - 4 - horizontalHalfExtent(entity);
        // Floating arithmetic at an inset edge may produce a tiny negative value.
        return (int) Math.max(0, Math.min(Math.min(16, settings.searchRadius()),
                Math.min(Math.max(0, requestedRadius), Math.floor(Math.max(0, remaining)))));
    }

    Decision creation(Entity entity, World destination, List<BlockState> blocks) {
        if (entity == null || blocks == null || blocks.isEmpty()) return Decision.deny("unsafe");
        Decision origin = origin(entity.getLocation(), destination, status());
        if (!origin.allowed()) return origin;
        try {
            Bounds area = allowed(destination);
            for (BlockState block : blocks) {
                if (block == null || !destination.equals(block.getWorld())
                        || block.getY() < destination.getMinHeight() || block.getY() >= destination.getMaxHeight()
                        || !area.contains(block.getX(), block.getZ())
                        || !area.contains(block.getX() + 1.0, block.getZ() + 1.0)) return Decision.deny("unsafe");
            }
            return Decision.allow(0);
        } catch (IllegalArgumentException e) { return Decision.deny("unsafe"); }
    }

    Decision position(Location at) {
        Status state = status();
        if (!state.ready()) return Decision.deny("unavailable");
        World target = isNether(at) ? state.overworld() : state.nether();
        return origin(at, target, state);
    }
}
