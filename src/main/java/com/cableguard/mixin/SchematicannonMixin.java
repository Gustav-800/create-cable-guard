package com.cableguard.mixin;

import com.cableguard.PrintSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refreshes the print session when the cannon reports progress. We intentionally do NOT end the
 * session on completion: blocks that were launched last are still flying and may land several
 * ticks after {@code finishedPrinting}, and their partner must not be pruned in the meantime.
 * The session expires on its own after {@link PrintSession}'s timeout.
 */
@Mixin(targets = "com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity")
public class SchematicannonMixin {

    @Inject(method = "finishedPrinting", at = @At("HEAD"))
    private void cableguard$keepSessionAliveOnFinish(CallbackInfo ci) {
        PrintSession.ping();
    }

    @Inject(method = "resetPrinter", at = @At("HEAD"))
    private void cableguard$keepSessionAliveOnReset(CallbackInfo ci) {
        PrintSession.ping();
    }
}
