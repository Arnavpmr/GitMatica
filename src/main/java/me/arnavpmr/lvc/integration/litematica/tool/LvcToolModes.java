package me.arnavpmr.lvc.integration.litematica.tool;

import java.util.Objects;
import javax.annotation.Nullable;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.tool.ToolMode;

public final class LvcToolModes
{
    @Nullable private static ToolMode editProject;

    private LvcToolModes()
    {
    }

    public static void installEditProject(ToolMode mode)
    {
        Objects.requireNonNull(mode, "mode");

        if (editProject != null && editProject != mode)
        {
            throw new IllegalStateException("Gitmatica Edit Project tool mode was installed twice");
        }

        editProject = mode;
    }

    public static boolean isEditProject(@Nullable ToolMode mode)
    {
        return editProject != null && mode == editProject;
    }

    public static boolean isEditProjectActive()
    {
        return isEditProject(DataManager.getToolMode());
    }
}
