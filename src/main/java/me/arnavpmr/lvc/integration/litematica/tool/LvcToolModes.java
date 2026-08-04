package me.arnavpmr.lvc.integration.litematica.tool;

import java.util.Objects;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.tool.ToolMode;

public final class LvcToolModes
{
    @Nullable private static ToolMode editSubregions;

    private LvcToolModes()
    {
    }

    public static void installEditSubregions(ToolMode mode)
    {
        Objects.requireNonNull(mode, "mode");

        if (editSubregions != null && editSubregions != mode)
        {
            throw new IllegalStateException("Gitmatica Edit Subregions tool mode was installed twice");
        }

        editSubregions = mode;
    }

    public static boolean isEditSubregions(@Nullable ToolMode mode)
    {
        return editSubregions != null && mode == editSubregions;
    }

    public static boolean isEditSubregionsActive()
    {
        return isEditSubregions(DataManager.getToolMode());
    }
}
