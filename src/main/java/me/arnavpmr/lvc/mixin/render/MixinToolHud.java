package me.arnavpmr.lvc.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.render.infohud.InfoHud;
import fi.dy.masa.litematica.render.infohud.ToolHud;
import me.arnavpmr.lvc.integration.litematica.tool.LvcEditProjectToolHud;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;

@Mixin(ToolHud.class)
abstract class MixinToolHud extends InfoHud
{
    @Shadow
    protected abstract boolean hasEnabledTool();

    @Inject(method = "updateHudText", at = @At("HEAD"), cancellable = true)
    private void gitmatica$renderEditProjectHud(CallbackInfo callbackInfo)
    {
        if (!LvcToolModes.isEditProjectActive())
        {
            return;
        }

        if (this.hasEnabledTool())
        {
            LvcEditProjectToolHud.appendLines(this.lineList);
        }

        callbackInfo.cancel();
    }
}
