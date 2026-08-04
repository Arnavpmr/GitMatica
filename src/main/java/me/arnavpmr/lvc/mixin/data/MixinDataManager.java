package me.arnavpmr.lvc.mixin.data;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.tool.ToolMode;
import me.arnavpmr.lvc.integration.litematica.selection.LvcSubRegionEditSession;

@Mixin(DataManager.class)
abstract class MixinDataManager
{
    @Inject(method = "setToolMode", at = @At("TAIL"))
    private static void gitmatica$resetTransientSubRegionSelection(
            ToolMode mode,
            CallbackInfo callbackInfo)
    {
        LvcSubRegionEditSession.onToolModeChanged(mode);
    }
}
