package com.cableguard.mixin;

import com.cableguard.PrintSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * createaddition 1.7.x keeps wire links in each connector's block entity NBT, but its
 * {@code WireGraph} also verifies links against the world. When the partner block entity is not
 * present yet (because a schematic cannon is still printing it), the graph marks the position as
 * missing and deletes the wire on the same tick. Skip that pruning while a print session is active.
 */
@Mixin(targets = "com.mrh0.createaddition.energy.network.WireGraph")
public class WireGraphMixin {

    @Inject(method = "pruneDanglingWires", at = @At("HEAD"), cancellable = true)
    private void cableguard$deferPruneDuringPrint(CallbackInfo ci) {
        if (PrintSession.active()) {
            ci.cancel();
        }
    }
}
