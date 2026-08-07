package me.arnavpmr.lvc.mixin.inventory;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.arnavpmr.lvc.integration.litematica.verifier.VerifierInventoryOverlay;

/**
 * Prevents Tech Utils and Gitmatica from coloring the same container slot.
 */
@Pseudo
@Mixin(
        targets = "dev.kikugie.techutils.feature.containerscan.verifier.InventoryOverlay",
        remap = false)
abstract class MixinTechUtilsInventoryOverlay
{
    @Inject(
            method = "drawStack",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void gitmatica$yieldBoundScreenOverlay(
            GuiGraphicsExtractor graphics,
            Slot slot,
            ItemStack stack,
            CallbackInfoReturnable<ItemStack> callbackInfo)
    {
        if (VerifierInventoryOverlay.hasBoundScreenOverlay())
        {
            callbackInfo.setReturnValue(stack);
        }
    }
}
