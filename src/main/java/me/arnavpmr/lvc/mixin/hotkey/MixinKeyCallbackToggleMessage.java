package me.arnavpmr.lvc.mixin.hotkey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;

/** Prevents Edit Project's transient selection from changing its own shape. */
@Mixin(targets = "fi.dy.masa.litematica.event.KeyCallbacks$KeyCallbackToggleMessage")
abstract class MixinKeyCallbackToggleMessage
{
    @Inject(method = "onKeyAction", at = @At("HEAD"), cancellable = true)
    private void gitmatica$blockUnsupportedProjectSelectionActions(
            KeyAction action,
            IKeybind key,
            CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (!LvcToolModes.isEditProjectActive())
        {
            return;
        }

        if (key == Hotkeys.ADD_SELECTION_BOX.getKeybind() ||
                key == Hotkeys.DELETE_SELECTION_BOX.getKeybind() ||
                key == Hotkeys.MOVE_ENTIRE_SELECTION.getKeybind() ||
                key == Hotkeys.SET_AREA_ORIGIN.getKeybind())
        {
            callbackInfo.setReturnValue(true);
        }
    }
}
