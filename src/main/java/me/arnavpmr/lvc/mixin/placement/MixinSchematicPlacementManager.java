package me.arnavpmr.lvc.mixin.placement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import me.arnavpmr.lvc.overlay.LvcSubRegionStructuralDiffRegistry;

/** Keeps retired-only structural chunks in Litematica's normal load lifecycle. */
@Mixin(SchematicPlacementManager.class)
abstract class MixinSchematicPlacementManager
{
    @Inject(method = "onClientChunkLoad", at = @At("TAIL"))
    private void gitmatica$loadRetiredStructuralChunk(
            int chunkX,
            int chunkZ,
            CallbackInfo callbackInfo)
    {
        if (LvcSubRegionStructuralDiffRegistry.hasRetiredBlocksInChunk(
                chunkX, chunkZ))
        {
            ((SchematicPlacementManager) (Object) this)
                    .markChunkForRebuild(chunkX, chunkZ);
        }
    }

    @Inject(method = "markChunkForUnload(II)V", at = @At("HEAD"), cancellable = true)
    private void gitmatica$keepRetiredStructuralChunkLoaded(
            int chunkX,
            int chunkZ,
            CallbackInfo callbackInfo)
    {
        if (LvcSubRegionStructuralDiffRegistry.hasRetiredBlocksInChunk(
                chunkX, chunkZ))
        {
            callbackInfo.cancel();
        }
    }
}
