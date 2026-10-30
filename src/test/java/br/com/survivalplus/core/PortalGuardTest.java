package br.com.survivalplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.survivalplus.core.portal.Bounds;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/** Exercises portal policy against the real Paper interfaces without a server or Mockito. */
final class PortalGuardTest {
    private record Fixture(PortalSettings settings, World overworld, World nether, PortalGuard guard) {}

    private static Fixture fixture() throws Exception {
        return fixture(null);
    }

    private static Fixture fixture(String statusProblem) throws Exception {
        InputStream input = PortalGuardTest.class.getResourceAsStream("/config.yml");
        assertNotNull(input);
        YamlConfiguration configuration = new YamlConfiguration();
        try (input; InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        }
        PortalSettings settings = PortalSettings.read(configuration);
        World overworld = world(settings.overworld(), World.Environment.NORMAL, -64, 320, 0, 0, 15000);
        World nether = world(settings.nether(), World.Environment.NETHER, 0, 256, 0, 0, 8192);
        return fixture(settings, overworld, nether, statusProblem);
    }

    private static Fixture fixture(PortalSettings settings, World overworld, World nether, String problem) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        PortalGuard guard = new PortalGuard(logger, settings,
                () -> new PortalGuard.Status(overworld, nether, 1.0, problem == null ? 6.0 : 8.0,
                        problem == null ? 6.0 : 8.0, problem));
        return new Fixture(settings, overworld, nether, guard);
    }

    private static Location at(World world, double x, double z) {
        return new Location(world, x, 70, z);
    }

    private static World world(NamespacedKey key, World.Environment environment, int minY, int maxY,
                               double centerX, double centerZ, double borderSize) {
        WorldBorder border = proxy(WorldBorder.class, (self, method, args) -> switch (method.getName()) {
            case "getCenter" -> new Location(null, centerX, 0, centerZ);
            case "getSize" -> borderSize;
            default -> throw unexpected(method);
        });
        return proxy(World.class, (self, method, args) -> switch (method.getName()) {
            case "getKey" -> key;
            case "getEnvironment" -> environment;
            case "getName" -> key.getKey();
            case "getWorldBorder" -> border;
            case "getMinHeight" -> minY;
            case "getMaxHeight" -> maxY;
            case "isChunkGenerated", "isChunkLoaded", "getChunkAt", "getBlockAt", "getHighestBlockAt",
                    "getHighestBlockYAt", "loadChunk", "regenerateChunk" ->
                    throw new AssertionError("A política não pode consultar/gerar chunks ou terreno: " + method.getName());
            default -> throw unexpected(method);
        });
    }

    private static Entity entity(Location origin) {
        UUID id = UUID.randomUUID();
        return proxy(Entity.class, (self, method, args) -> switch (method.getName()) {
            case "getLocation" -> origin.clone();
            case "getWorld" -> origin.getWorld();
            case "getUniqueId" -> id;
            case "getBoundingBox" -> new BoundingBox(origin.getX() - 0.3, origin.getY(), origin.getZ() - 0.3,
                    origin.getX() + 0.3, origin.getY() + 1.8, origin.getZ() + 0.3);
            case "getWidth" -> 0.6;
            case "getHeight" -> 1.8;
            default -> throw unexpected(method);
        });
    }

    private static BlockState block(World world, int x, int y, int z) {
        return proxy(BlockState.class, (self, method, args) -> switch (method.getName()) {
            case "getWorld" -> world;
            case "getX" -> x;
            case "getY" -> y;
            case "getZ" -> z;
            case "getLocation" -> new Location(world, x, y, z);
            default -> throw unexpected(method);
        });
    }

    private static AssertionError unexpected(Method method) {
        return new AssertionError("Método não previsto na fixture: " + method);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        Object value = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName() + "Fixture";
                    default -> throw unexpected(method);
                };
            }
            return handler.invoke(self, method, args);
        });
        return type.cast(value);
    }

    @Test
    void interiorAllowsBothDirectionsWithoutAnyTerrainOrChunkQuery() throws Exception {
        Fixture f = fixture();
        Location nether = at(f.nether(), 1000, 500);
        Location overworld = at(f.overworld(), 6000, 3000);
        assertTrue(f.guard().initial(entity(nether), nether, overworld, 128).allowed());
        assertTrue(f.guard().initial(entity(overworld), overworld, nether, 128).allowed());
        assertEquals(16, f.guard().initial(entity(overworld), overworld, nether, 128).radius());
        assertTrue(f.guard().finalExit(entity(nether), nether, overworld).allowed());
        assertTrue(f.guard().finalExit(entity(overworld), overworld, nether).allowed());
    }

    @Test
    void existingAndNewPortalsMayBeUsedBeforeDestinationChunksAreGenerated() throws Exception {
        // Every chunk/terrain accessor on these worlds throws, so a passing decision
        // catches the old mandatory pre-generation regression directly.
        Fixture f = fixture();
        Location from = at(f.overworld(), 0, 0);
        Location to = at(f.nether(), 0, 0);
        Entity player = entity(from);
        assertTrue(f.guard().initial(player, from, to, 16).allowed());
        assertTrue(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 0, 70, 0))).allowed());
    }

    @Test
    void fullVraktusProjectionIncludesTheFormerScreenshotBorderAt7400() throws Exception {
        Fixture f = fixture();
        Location from = at(f.overworld(), 7400, 0);
        Location target = at(f.nether(), 7400.0 / 6, 0);
        assertTrue(f.guard().initial(entity(from), from, target, 16).allowed());
        assertTrue(f.guard().finalExit(entity(from), from, target).allowed());
        assertTrue(f.guard().initial(entity(target), target, from, 16).allowed());
        assertTrue(f.guard().finalExit(entity(target), target, from).allowed());
    }

    @Test
    void returnSearchUsesOverworldUnitsSoEdgeCreationCanFindTheOriginalPortal() throws Exception {
        Fixture f = fixture();
        Location from = at(f.nether(), 0, 1079);
        Location theoretical = at(f.overworld(), 0, 6474);
        Entity player = entity(from);
        PortalGuard.Decision decision = f.guard().initial(player, from, theoretical, 128);
        assertTrue(decision.allowed());
        // The configured sixteen Nether blocks correspond to ninety-six Overworld blocks.
        assertEquals(96, decision.radius());
        assertTrue(f.guard().finalExit(player, from, at(f.overworld(), 0, 6496)).allowed());
    }

    @Test
    void negativeCoordinatesAndAllFourCornersUseTheSameRules() throws Exception {
        Fixture f = fixture();
        for (int signX : new int[]{-1, 1}) for (int signZ : new int[]{-1, 1}) {
            Location from = at(f.overworld(), signX * 7400, signZ * 6400);
            Location to = at(f.nether(), from.getX() / 6, from.getZ() / 6);
            assertTrue(f.guard().initial(entity(from), from, to, 16).allowed(), from.toString());
            assertTrue(f.guard().finalExit(entity(from), from, to).allowed(), from.toString());
            assertTrue(f.guard().initial(entity(to), to, from, 16).allowed(), to.toString());
        }
    }

    @Test
    void genuinelyOutsideThePhysicalMapOrItsNetherProjectionIsDenied() throws Exception {
        Fixture f = fixture();
        Location outsideNether = at(f.nether(), 1300, 0);
        assertFalse(f.guard().initial(entity(outsideNether), outsideNether, at(f.overworld(), 7800, 0), 16).allowed());
        Location outsideOverworld = at(f.overworld(), 7800, 0);
        assertFalse(f.guard().initial(entity(outsideOverworld), outsideOverworld, at(f.nether(), 1300, 0), 16).allowed());
        Location outsideZ = at(f.nether(), 0, -1100);
        assertFalse(f.guard().initial(entity(outsideZ), outsideZ, at(f.overworld(), 0, -6600), 16).allowed());
    }

    @Test
    void initialTargetCannotApplyASecondConversionOrIgnoreTheRealScale() throws Exception {
        Fixture f = fixture();
        Location from = at(f.overworld(), 6000, -3000);
        Entity player = entity(from);
        assertFalse(f.guard().initial(player, from, at(f.nether(), 750, -375), 16).allowed());
        assertFalse(f.guard().initial(player, from, at(f.nether(), 1000.0 / 6, -500.0 / 6), 16).allowed());
        assertTrue(f.guard().initial(player, from, at(f.nether(), 1000, -500), 16).allowed());
    }

    @Test
    void creationSearchTargetIsClonedAndClampedInsideEveryConnectionCorner() throws Exception {
        Fixture f = fixture();
        Bounds allowed = f.settings().connectionZone();
        for (double x : new double[]{allowed.minX() + 0.1, allowed.maxX() - 0.1}) {
            for (double z : new double[]{allowed.minZ() + 0.1, allowed.maxZ() - 0.1}) {
                Location target = at(f.nether(), x, z);
                Entity player = entity(at(f.overworld(), x * 6, z * 6));
                Location adjusted = f.guard().creationTarget(player, target);
                assertNotSame(target, adjusted);
                assertEquals(x, target.getX());
                assertEquals(z, target.getZ());
                assertEquals(target.getY(), adjusted.getY());
                assertEquals(f.nether(), adjusted.getWorld());
                assertTrue(allowed.inset(4).contains(adjusted.getX(), adjusted.getZ()));
                int radius = f.guard().creationRadius(player, adjusted, 128);
                assertTrue(radius >= 0 && radius <= 16);
                assertTrue(allowed.inset(4).contains(adjusted.getX() - radius, adjusted.getZ() - radius));
                assertTrue(allowed.inset(4).contains(adjusted.getX() + radius, adjusted.getZ() + radius));
            }
        }
    }

    @Test
    void interiorCreationRadiusRespectsTheCallerAndTheConfiguredMaximum() throws Exception {
        Fixture f = fixture();
        Entity player = entity(at(f.overworld(), 0, 0));
        Location target = at(f.nether(), 0, 0);
        assertEquals(16, f.guard().creationRadius(player, target, 128));
        assertEquals(3, f.guard().creationRadius(player, target, 3));
        assertEquals(0, f.guard().creationRadius(player, target, 0));
    }

    @Test
    void automaticCreationAcceptsAllSafeProposedBlocksInBothDimensions() throws Exception {
        Fixture f = fixture();
        Entity fromOverworld = entity(at(f.overworld(), 6000, 3000));
        List<BlockState> netherBlocks = List.of(block(f.nether(), 1000, 70, 500), block(f.nether(), 1001, 75, 500));
        assertTrue(f.guard().creation(fromOverworld, f.nether(), netherBlocks).allowed());
        Entity fromNether = entity(at(f.nether(), 1000, 500));
        assertTrue(f.guard().creation(fromNether, f.overworld(),
                List.of(block(f.overworld(), 6000, 70, 3000), block(f.overworld(), 6001, 75, 3000))).allowed());
    }

    @Test
    void oneUnsafeBlockCancelsTheEntireProposedPortal() throws Exception {
        Fixture f = fixture();
        Entity player = entity(at(f.overworld(), 7400, 0));
        BlockState safe = block(f.nether(), 1240, 70, 0);
        for (BlockState unsafe : List.of(block(f.nether(), 1250, 70, 0),
                block(f.nether(), -1251, 70, 0), block(f.nether(), 1240, 70, 1084),
                block(f.nether(), 1240, 70, -1084))) {
            assertFalse(f.guard().creation(player, f.nether(), List.of(safe, unsafe)).allowed());
        }
    }

    @Test
    void fullBlockVolumeRatherThanOnlyItsCenterMustFitTheConnectionZone() throws Exception {
        Fixture f = fixture();
        Entity player = entity(at(f.overworld(), 7400, 0));
        // Positive block x=1250 occupies [1250,1251], outside +1250.
        assertFalse(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 1250, 70, 0))).allowed());
        assertTrue(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 1249, 70, 0))).allowed());
        // At the negative side, block -1250 occupies [-1250,-1249] and is safe.
        Entity negativePlayer = entity(at(f.overworld(), -7400, 0));
        assertTrue(f.guard().creation(negativePlayer, f.nether(), List.of(block(f.nether(), -1250, 70, 0))).allowed());
        assertFalse(f.guard().creation(negativePlayer, f.nether(), List.of(block(f.nether(), -1251, 70, 0))).allowed());
    }

    @Test
    void creationCannotUseAnotherWorldsBlocksOrExceedItsVerticalRange() throws Exception {
        Fixture f = fixture();
        Entity player = entity(at(f.overworld(), 0, 0));
        assertFalse(f.guard().creation(player, f.nether(), List.of(block(f.overworld(), 0, 70, 0))).allowed());
        assertFalse(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 0, -1, 0))).allowed());
        assertFalse(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 0, 256, 0))).allowed());
        assertTrue(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 0, 0, 0), block(f.nether(), 0, 255, 0))).allowed());
    }

    @Test
    void portalCreationFromOutsideTheNetherZoneIsDeniedEvenForSafeBlocks() throws Exception {
        Fixture f = fixture();
        Entity player = entity(at(f.nether(), 1300, 0));
        assertFalse(f.guard().creation(player, f.overworld(), List.of(block(f.overworld(), 0, 70, 0))).allowed());
    }

    @Test
    void finalExitRechecksTheActualPortalInsteadOfTrustingTheInitialTarget() throws Exception {
        Fixture f = fixture();
        Location from = at(f.overworld(), 7490, 0);
        Entity player = entity(from);
        assertTrue(f.guard().initial(player, from, at(f.nether(), 7490.0 / 6, 0), 16).allowed());
        assertFalse(f.guard().finalExit(player, from, at(f.nether(), 1251, 0)).allowed());
        // Feet inside the zone are insufficient when part of the entity extends outside.
        assertFalse(f.guard().finalExit(player, from, at(f.nether(), 1249.8, 0)).allowed());
        assertTrue(f.guard().finalExit(player, from, at(f.nether(), 1248, 0)).allowed());
    }

    @Test
    void actualWorldBorderIsEnforcedEvenWhenSmallerThanConfiguredMapBounds() throws Exception {
        Fixture base = fixture();
        World smallerNether = world(base.settings().nether(), World.Environment.NETHER, 0, 256, 0, 0, 2000);
        Fixture f = fixture(base.settings(), base.overworld(), smallerNether, null);
        Location from = at(f.overworld(), 6060, 0);
        assertFalse(f.guard().initial(entity(from), from, at(smallerNether, 1010, 0), 16).allowed());
        Entity player = entity(at(f.overworld(), 5940, 0));
        assertFalse(f.guard().creation(player, smallerNether, List.of(block(smallerNether, 1000, 70, 0))).allowed());
        assertFalse(f.guard().finalExit(player, at(f.overworld(), 5940, 0), at(smallerNether, 1000, 0)).allowed());
    }

    @Test
    void invalidScaleStatusBlocksInitialFinalAndAutomaticCreation() throws Exception {
        Fixture f = fixture("Escala real 1:8 difere da esperada 1:6");
        Location from = at(f.overworld(), 0, 0);
        Entity player = entity(from);
        PortalGuard.Decision initial = f.guard().initial(player, from, at(f.nether(), 0, 0), 16);
        assertFalse(initial.allowed());
        assertEquals("unavailable", initial.message());
        assertFalse(f.guard().finalExit(player, from, at(f.nether(), 0, 0)).allowed());
        assertFalse(f.guard().creation(player, f.nether(), List.of(block(f.nether(), 0, 70, 0))).allowed());
    }

    @Test
    void endPortalsRemainOutsideTheNetherPolicy() throws Exception {
        Fixture f = fixture();
        World end = world(NamespacedKey.minecraft("the_end"), World.Environment.THE_END, 0, 256, 0, 0, 10000);
        assertFalse(f.guard().entityPortalInScope(at(f.overworld(), 0, 0), at(end, 0, 0)));
        assertFalse(f.guard().entityPortalInScope(at(end, 0, 0), at(f.overworld(), 0, 0)));
        assertTrue(f.guard().entityPortalInScope(at(f.overworld(), 0, 0), at(f.nether(), 0, 0)));
    }
}
