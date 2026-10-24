package com.cableguard.mixin;

import com.cableguard.PrintSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create 6.x filters block entity data when printing: only blocks in {@code create:safe_nbt},
 * types registered in {@code SafeNbtWriterRegistry}, or {@code PartialSafeNBT} block entities get
 * any data applied. {@code AbstractConnectorBlockEntity} inherits {@code writeSafe} from
 * {@code SmartBlockEntity}, and that implementation never writes createaddition's {@code nodes}
 * list, so printed connectors are created blank and their wires are lost.
 * <p>
 * This mixin substitutes a tag containing only the (relative, position-safe) {@code nodes} list
 * for any createaddition block entity.
 */
@Mixin(targets = "com.simibubi.create.foundation.utility.BlockHelper")
public class BlockHelperMixin {

    @Inject(
            method = "prepareBlockEntityData(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cableguard$carryWireNodes(Level level, BlockState state, BlockEntity be,
                                                 CallbackInfoReturnable<CompoundTag> cir) {
        PrintSession.ping();
        if (level == null || be == null) {
            return;
        }
        if (!be.getClass().getName().startsWith("com.mrh0.createaddition.")) {
            return;
        }
        CompoundTag full = be.saveWithFullMetadata(level.registryAccess());
        if (!full.contains("nodes")) {
            return;
        }
        CompoundTag out = new CompoundTag();
        out.put("nodes", full.get("nodes"));
        cir.setReturnValue(out);
    }
}
