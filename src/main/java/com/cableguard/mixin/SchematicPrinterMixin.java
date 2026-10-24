package com.cableguard.mixin;

import com.cableguard.WireNodeTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Create rotates schematic block positions and block states, but does not rotate arbitrary block
 * entity NBT. createaddition stores wire endpoints as relative offsets ({@code nodes[x,y,z]}), so a
 * rotated blueprint would otherwise reconnect the wrong positions.
 * <p>
 * After the printer has built its in-memory copy of the schematic, rotate those offsets with the
 * same {@link StructurePlaceSettings} that Create used for the block states.
 */
@Mixin(targets = "com.simibubi.create.content.schematics.SchematicPrinter")
public class SchematicPrinterMixin {

    private static final Logger LOG = LoggerFactory.getLogger("CableGuard");

    @Inject(method = "loadSchematic", at = @At("RETURN"))
    private void cableguard$rotateWireNodes(ItemStack blueprint, Level originalWorld, boolean processNBT, CallbackInfo ci) {
        try {
            Object reader = blockReaderOf(this);
            if (reader == null) {
                return;
            }
            StructurePlaceSettings settings = settingsOf(blueprint, processNBT);
            if (settings == null) {
                return;
            }
            Mirror mirror = settings.getMirror();
            Rotation rotation = settings.getRotation();
            if (WireNodeTransform.isIdentity(mirror, rotation)) {
                return;
            }

            Method getBlockEntities = reader.getClass().getMethod("getBlockEntities");
            Iterable<?> blockEntities = (Iterable<?>) getBlockEntities.invoke(reader);
            int changed = 0;
            for (Object candidate : blockEntities) {
                if (!(candidate instanceof BlockEntity be)) {
                    continue;
                }
                if (!be.getClass().getName().startsWith("com.mrh0.createaddition.")) {
                    continue;
                }
                CompoundTag current = be.saveWithFullMetadata(originalWorld.registryAccess());
                if (!current.contains("nodes")) {
                    continue;
                }
                ListTag nodes = current.getList("nodes", Tag.TAG_COMPOUND).copy();
                CompoundTag replacement = new CompoundTag();
                replacement.put("nodes", nodes);
                WireNodeTransform.rotateInPlace(replacement, mirror, rotation);
                be.loadWithComponents(replacement, originalWorld.registryAccess());
                changed++;
            }
            if (changed > 0) {
                LOG.info("[CABLEGUARD] rotated wire nodes of {} block entities (rotation={}, mirror={})",
                        changed, rotation, mirror);
            }
        } catch (Throwable t) {
            LOG.warn("[CABLEGUARD] failed to rotate wire nodes for rotated schematic", t);
        }
    }

    private static Object blockReaderOf(Object printer) throws Exception {
        Field field = printer.getClass().getDeclaredField("blockReader");
        field.setAccessible(true);
        return field.get(printer);
    }

    private static StructurePlaceSettings settingsOf(ItemStack blueprint, boolean processNBT) throws Exception {
        Class<?> schematicItem = Class.forName("com.simibubi.create.content.schematics.SchematicItem");
        Method method = schematicItem.getMethod("getSettings", ItemStack.class, boolean.class);
        return (StructurePlaceSettings) method.invoke(null, blueprint, processNBT);
    }
}
