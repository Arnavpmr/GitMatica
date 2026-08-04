package me.arnavpmr.lvc.mixin.selection;

import javax.annotation.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.SelectionManager;
import fi.dy.masa.malilib.gui.GuiBase;
import me.arnavpmr.lvc.integration.litematica.selection.LvcSubRegionEditSession;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;

@Mixin(SelectionManager.class)
abstract class MixinSelectionManager
{
    @Inject(method = "getCurrentSelection", at = @At("HEAD"), cancellable = true)
    private void gitmatica$getTransientSubRegionSelection(
            CallbackInfoReturnable<AreaSelection> callbackInfo)
    {
        if (LvcToolModes.isEditSubregionsActive())
        {
            callbackInfo.setReturnValue(LvcSubRegionEditSession.currentSelection());
        }
    }

    @Inject(method = { "getCurrentSelectionId", "getCurrentNormalSelectionId" },
            at = @At("HEAD"), cancellable = true)
    private void gitmatica$hideLitematicaSelectionId(
            CallbackInfoReturnable<String> callbackInfo)
    {
        if (LvcToolModes.isEditSubregionsActive())
        {
            callbackInfo.setReturnValue(null);
        }
    }

    @Inject(method = "setCurrentSelection", at = @At("HEAD"), cancellable = true)
    private void gitmatica$preventLitematicaSelectionChange(
            @Nullable String selectionId,
            CallbackInfo callbackInfo)
    {
        if (LvcToolModes.isEditSubregionsActive())
        {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "getEditGui", at = @At("HEAD"), cancellable = true)
    private void gitmatica$hideTransientSelectionFromAreaEditor(
            CallbackInfoReturnable<GuiBase> callbackInfo)
    {
        if (LvcToolModes.isEditSubregionsActive())
        {
            callbackInfo.setReturnValue(null);
        }
    }
}
