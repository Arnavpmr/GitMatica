package me.arnavpmr.lvc.overlay;

import net.minecraft.core.BlockPos;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;

/** Renders applied manual origins independently from tracking placement boxes. */
public final class LvcManualOriginRenderer
{
    private static final float EXPAND = 0.001f;
    private static final float LINE_WIDTH = 2f;
    private static final Color4f WORKING_ORANGE = color(0xFF9010);
    private static final Color4f HEAD_YELLOW = color(0xFFFF00);

    private LvcManualOriginRenderer()
    {
    }

    public static void render()
    {
        if (!Configs.Visuals.ENABLE_AREA_SELECTION_RENDERING.getBooleanValue())
        {
            return;
        }

        for (LvcManualOriginMarkerRegistry.Marker marker :
                LvcManualOriginMarkerRegistry.visibleMarkers())
        {
            if (marker.modified())
            {
                render(marker.head(), HEAD_YELLOW);
            }

            render(marker.working(), WORKING_ORANGE);
        }
    }

    private static void render(BlockPos position, Color4f color)
    {
        RenderUtils.renderBlockOutline(
                position, EXPAND, LINE_WIDTH, color, false);
    }

    private static Color4f color(int rgb)
    {
        return new Color4f(
                (rgb >> 16 & 0xFF) / 255f,
                (rgb >> 8 & 0xFF) / 255f,
                (rgb & 0xFF) / 255f);
    }
}
