package com.cableguard;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * In-game verification, enabled with {@code -Dcableguard.selftest=true}.
 * <p>
 * It runs three checks against a real integrated server:
 * <ol>
 *   <li>DATA: {@code BlockHelper.prepareBlockEntityData} returns the connector's {@code nodes}.</li>
 *   <li>CONTROL: without an active print session a freshly loaded one-sided wire is pruned
 *       (reproduces the bug).</li>
 *   <li>SURVIVE/RECONNECT: with an active print session the first endpoint keeps its wire while
 *       its partner is absent, and the link is intact once the partner is placed.</li>
 * </ol>
 */
public final class SelfTest {

    private static final Logger LOG = LoggerFactory.getLogger("CableGuard");

    private static boolean enabled;
    private static ServerLevel level;
    private static int phase = -1;
    private static int timer;

    private static BlockPos pData;
    private static BlockPos pCtrl;
    private static BlockPos pFix;
    private static BlockPos pFixTarget;

    private static boolean rotationOk;
    private static boolean rotHookOk;
    private static boolean dataOk;
    private static boolean ctrlOk;
    private static boolean surviveOk;
    private static boolean reconnectOk;

    private SelfTest() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty("cableguard.selftest") == null) {
            return;
        }
        try {
            level = event.getServer().overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            pData = new BlockPos(spawn.getX() + 32, 220, spawn.getZ() + 64);
            pCtrl = new BlockPos(spawn.getX() + 48, 220, spawn.getZ() + 64);
            pFix = new BlockPos(spawn.getX() + 64, 220, spawn.getZ() + 64);
            pFixTarget = pFix.offset(3, 0, 0);
            phase = 0;
            timer = 0;
            enabled = true;
            LOG.info("[CABLEGUARD] selftest start: data={} control={} fix={}+{}",
                    pData, pCtrl, pFix, pFixTarget);
        } catch (Throwable t) {
            LOG.error("[CABLEGUARD] selftest init failed", t);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!enabled || phase < 0) {
            return;
        }
        if (event.getServer().overworld() != level) {
            return;
        }
        if (timer > 0) {
            timer--;
            return;
        }
        try {
            step(event.getServer());
        } catch (Throwable t) {
            LOG.error("[CABLEGUARD] selftest step failed", t);
            finish(event.getServer());
        }
    }

    private static void step(MinecraftServer server) {
        switch (phase) {
            case 0 -> {
                rotationOk = rotationTest();
                rotHookOk = rotationHookTest();
                BlockEntity be = place(pData, 3);
                boolean placed = be != null;
                boolean inBe = placed && hasNodes(be);
                boolean inTag = placed && preparedHasNodes(be);
                dataOk = inBe && inTag;
                LOG.info("[CABLEGUARD] DATA_TEST placed={} nodesInBe={} nodesInPrintTag={} -> {}",
                        placed, inBe, inTag, dataOk ? "PASS" : "FAIL");

                PrintSession.end();
                place(pCtrl, 3);
                phase = 1;
                timer = 40;
            }
            case 1 -> {
                boolean pruned = !hasNodesAt(pCtrl);
                ctrlOk = pruned;
                LOG.info("[CABLEGUARD] CONTROL no-session nodePruned={} -> {}",
                        pruned, pruned ? "PASS" : "WARN");
                level.removeBlock(pCtrl, false);

                PrintSession.begin();
                place(pFix, 3);
                phase = 2;
                timer = 40;
            }
            case 2 -> {
                boolean kept = hasNodesAt(pFix);
                surviveOk = kept;
                LOG.info("[CABLEGUARD] SURVIVE_IN_SESSION nodeKept={} -> {}", kept, kept ? "PASS" : "FAIL");
                place(pFixTarget, -3);
                phase = 3;
                timer = 20;
            }
            case 3 -> {
                boolean a = hasNodesAt(pFix);
                boolean b = hasNodesAt(pFixTarget);
                reconnectOk = a && b;
                LOG.info("[CABLEGUARD] RECONNECT a={} b={} -> {}", a, b, reconnectOk ? "PASS" : "FAIL");
                PrintSession.end();
                finish(server);
            }
            default -> finish(server);
        }
    }

    /**
     * End-to-end check of the printer hook: builds a two-connector structure in the world, writes
     * it to the server's uploaded-schematics folder, creates a rotated blueprint item and asks
     * Create's real {@code SchematicPrinter} to load it. The block entities inside the printer must
     * end up with rotated wire offsets.
     */
    private static boolean rotationHookTest() {
        BlockPos rig = new BlockPos(level.getSharedSpawnPos().getX() + 96, 220, level.getSharedSpawnPos().getZ() + 64);
        BlockPos rigB = rig.offset(3, 0, 0);
        Path file = null;
        try {
            BlockEntity a = place(rig, 3);
            BlockEntity b = place(rigB, -3);
            if (a == null || b == null) {
                return false;
            }

            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, rig, new Vec3i(4, 1, 1), true, Blocks.AIR);

            Path uploaded = (Path) Class.forName("com.simibubi.create.foundation.utility.CreatePaths")
                    .getField("UPLOADED_SCHEMATICS_DIR")
                    .get(null);
            Path ownerDir = uploaded.resolve("cableguard");
            Files.createDirectories(ownerDir);
            file = ownerDir.resolve("selftest_rot.nbt");
            try (OutputStream out = Files.newOutputStream(file)) {
                NbtIo.writeCompressed(template.save(new CompoundTag()), out);
            }

            Class<?> components = Class.forName("com.simibubi.create.AllDataComponents");
            Item item = BuiltInRegistries.ITEM
                    .getOptional(ResourceLocation.fromNamespaceAndPath("create", "schematic"))
                    .orElse(Items.AIR);
            ItemStack stack = new ItemStack(item);
            setComponent(stack, components, "SCHEMATIC_DEPLOYED", Boolean.TRUE);
            setComponent(stack, components, "SCHEMATIC_OWNER", "cableguard");
            setComponent(stack, components, "SCHEMATIC_FILE", "selftest_rot.nbt");
            setComponent(stack, components, "SCHEMATIC_ANCHOR", rig);
            setComponent(stack, components, "SCHEMATIC_ROTATION", Rotation.CLOCKWISE_90);
            setComponent(stack, components, "SCHEMATIC_MIRROR", Mirror.NONE);

            Class<?> printerClass = Class.forName("com.simibubi.create.content.schematics.SchematicPrinter");
            Object printer = printerClass.getConstructor().newInstance();
            printerClass.getMethod("loadSchematic", ItemStack.class, Level.class, boolean.class)
                    .invoke(printer, stack, level, true);
            if (Boolean.TRUE.equals(printerClass.getMethod("isErrored").invoke(printer))) {
                LOG.warn("[CABLEGUARD] ROT_HOOK printer errored");
                return false;
            }
            Field readerField = printerClass.getDeclaredField("blockReader");
            readerField.setAccessible(true);
            Object reader = readerField.get(printer);
            if (reader == null) {
                LOG.warn("[CABLEGUARD] ROT_HOOK blockReader is null");
                return false;
            }

            BlockPos expectedA = WireNodeTransform.transform(new BlockPos(3, 0, 0), Mirror.NONE, Rotation.CLOCKWISE_90);
            BlockPos expectedB = new BlockPos(-expectedA.getX(), -expectedA.getY(), -expectedA.getZ());
            boolean foundA = false;
            boolean foundB = false;
            Iterable<?> blockEntities = (Iterable<?>) reader.getClass().getMethod("getBlockEntities").invoke(reader);
            for (Object candidate : blockEntities) {
                if (!(candidate instanceof BlockEntity be)) {
                    continue;
                }
                if (!be.getClass().getName().startsWith("com.mrh0.createaddition.")) {
                    continue;
                }
                CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                if (!tag.contains("nodes")) {
                    continue;
                }
                ListTag nodes = tag.getList("nodes", Tag.TAG_COMPOUND);
                if (nodes.isEmpty()) {
                    continue;
                }
                CompoundTag node = nodes.getCompound(0);
                BlockPos offset = new BlockPos(node.getInt("x"), node.getInt("y"), node.getInt("z"));
                foundA |= same(offset, expectedA);
                foundB |= same(offset, expectedB);
            }
            boolean ok = foundA && foundB;
            LOG.info("[CABLEGUARD] ROT_HOOK expected={} / {} foundA={} foundB={} -> {}",
                    expectedA, expectedB, foundA, foundB, ok ? "PASS" : "FAIL");
            return ok;
        } catch (Throwable t) {
            LOG.error("[CABLEGUARD] ROT_HOOK failed", t);
            return false;
        } finally {
            level.removeBlock(rig, false);
            level.removeBlock(rigB, false);
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void setComponent(ItemStack stack, Class<?> components, String name, T value) throws Exception {
        DataComponentType<T> type = (DataComponentType<T>) components.getField(name).get(null);
        stack.set(type, value);
    }

    /**
     * Verifies that {@link WireNodeTransform} maps offsets the same way vanilla rotates the
     * connector block state. For a connector facing NORTH with a wire to its facing side, the
     * rotated wire must point in the direction the rotated block now faces.
     */
    private static boolean rotationTest() {
        Block block = BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath("createaddition", "connector"))
                .orElse(Blocks.AIR);
        if (block == Blocks.AIR) {
            return false;
        }
        BlockState base = block.defaultBlockState();
        DirectionProperty facing = null;
        for (Property<?> property : base.getProperties()) {
            if (property instanceof DirectionProperty dp && property.getName().equals("facing")) {
                facing = dp;
                break;
            }
        }
        if (facing == null) {
            LOG.warn("[CABLEGUARD] ROT_TEST skipped: connector has no 'facing' property");
            return false;
        }
        BlockState north = base.setValue(facing, Direction.NORTH);
        BlockPos northOffset = new BlockPos(Direction.NORTH.getNormal());
        boolean ok = true;
        for (Mirror mirror : Mirror.values()) {
            for (Rotation rotation : Rotation.values()) {
                if (mirror == Mirror.NONE && rotation == Rotation.NONE) {
                    continue;
                }
                BlockState rotatedState = north.mirror(mirror).rotate(rotation);
                BlockPos expected = new BlockPos(rotatedState.getValue(facing).getNormal());
                BlockPos actual = WireNodeTransform.transform(northOffset, mirror, rotation);
                if (!same(actual, expected)) {
                    ok = false;
                    LOG.warn("[CABLEGUARD] ROT mismatch mirror={} rotation={} expected={} actual={}",
                            mirror, rotation, expected, actual);
                }
            }
        }
        LOG.info("[CABLEGUARD] ROT_TEST -> {}", ok ? "PASS" : "FAIL");
        return ok;
    }

    private static boolean same(BlockPos a, BlockPos b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private static BlockEntity place(BlockPos pos, int dx) {
        Block block = BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath("createaddition", "connector"))
                .orElse(Blocks.AIR);
        if (block == Blocks.AIR) {
            LOG.warn("[CABLEGUARD] createaddition:connector not registered");
            return null;
        }
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        ListTag nodes = new ListTag();
        CompoundTag node = new CompoundTag();
        node.putInt("id", 0);
        node.putInt("other", 0);
        node.putInt("type", 0);
        node.putInt("x", dx);
        node.putInt("y", 0);
        node.putInt("z", 0);
        nodes.add(node);
        tag.put("nodes", nodes);
        be.loadWithComponents(tag, level.registryAccess());
        return be;
    }

    private static boolean hasNodes(BlockEntity be) {
        CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
        return tag.getList("nodes", Tag.TAG_COMPOUND).size() > 0;
    }

    private static boolean hasNodesAt(BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && hasNodes(be);
    }

    private static boolean preparedHasNodes(BlockEntity be) {
        try {
            BlockState state = level.getBlockState(be.getBlockPos());
            Class<?> helper = Class.forName("com.simibubi.create.foundation.utility.BlockHelper");
            Object result = helper
                    .getMethod("prepareBlockEntityData", Level.class, BlockState.class, BlockEntity.class)
                    .invoke(null, level, state, be);
            if (result instanceof CompoundTag tag) {
                return tag.getList("nodes", Tag.TAG_COMPOUND).size() > 0;
            }
        } catch (Throwable t) {
            LOG.error("[CABLEGUARD] prepareBlockEntityData check failed", t);
        }
        return false;
    }

    private static void finish(MinecraftServer server) {
        enabled = false;
        phase = -1;
        PrintSession.end();
        for (BlockPos p : new BlockPos[]{pData, pCtrl, pFix, pFixTarget}) {
            if (p != null && level != null) {
                level.removeBlock(p, false);
            }
        }
        boolean all = rotationOk && rotHookOk && dataOk && ctrlOk && surviveOk && reconnectOk;
        LOG.info("[CABLEGUARD] RESULT rotationMath={} rotationHook={} data={} controlPruned={} survive={} reconnect={} => {}",
                rotationOk, rotHookOk, dataOk, ctrlOk, surviveOk, reconnectOk, all ? "ALL_PASS" : "FAIL");
        try {
            server.halt(false);
        } catch (Throwable t) {
            LOG.error("[CABLEGUARD] halt failed", t);
        }
    }
}
