package me.arnavpmr.lvc.mixin.tool;

import java.util.Arrays;
import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fi.dy.masa.litematica.tool.ToolMode;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;

@Mixin(ToolMode.class)
abstract class MixinToolMode
{
    private static final String INTERNAL_NAME = "EDIT_PROJECT";

    @Shadow @Final @Mutable private static ToolMode[] $VALUES;
    @Shadow @Final @Mutable private static ImmutableList<ToolMode> VALUES;

    @Invoker("<init>")
    public static ToolMode gitmatica$createToolMode(
            String internalName,
            int ordinal,
            String configName,
            String translationKey,
            boolean creativeOnly,
            boolean usesSchematic)
    {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void gitmatica$addEditProjectMode(CallbackInfo callbackInfo)
    {
        for (ToolMode mode : $VALUES)
        {
            if (INTERNAL_NAME.equals(mode.name()))
            {
                LvcToolModes.installEditProject(mode);
                return;
            }
        }

        ToolMode mode = gitmatica$createToolMode(
                INTERNAL_NAME,
                $VALUES.length,
                "edit_project",
                "gitmatica.tool_mode.name.edit_project",
                false,
                false
        );
        $VALUES = Arrays.copyOf($VALUES, $VALUES.length + 1);
        $VALUES[$VALUES.length - 1] = mode;
        VALUES = ImmutableList.copyOf($VALUES);
        LvcToolModes.installEditProject(mode);
    }
}
