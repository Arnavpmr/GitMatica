package me.arnavpmr.lvc.overlay;

import java.util.List;

import net.minecraft.core.BlockPos;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff.BoundsStatus;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;

/** Renders applied HEAD-to-working subregion bounds in Tool Mode 10. */
public final class LvcSubRegionStructuralBoundsRenderer
{
    private static final float EXPAND = 0.001f;
    private static final float LINE_WIDTH = 1f;
    private static final Color4f CHANGED_HEAD = color(0xFF9010);
    private static final Color4f CHANGED_WORKING = color(0x16FFFF);
    private static final Color4f ADDED = color(0x33CC33);
    private static final Color4f REMOVED = color(0xFF3333);

    private LvcSubRegionStructuralBoundsRenderer()
    {
    }

    public static void render()
    {
        if (!LvcToolModes.isEditSubregionsActive() ||
                !Configs.Visuals.ENABLE_PLACEMENT_BOXES_RENDERING.getBooleanValue())
        {
            return;
        }

        List<LvcSubRegionStructuralDiffRegistry.WorldBounds> bounds =
                LvcSubRegionStructuralDiffRegistry.selectedWorldBounds();

        for (LvcSubRegionStructuralDiffRegistry.WorldBounds value : bounds)
        {
            renderBounds(value.min(), value.max(), color(value.status()));
        }
    }

    private static void renderBounds(BlockPos min, BlockPos max, Color4f color)
    {
        if (min.equals(max))
        {
            RenderUtils.renderBlockOutline(
                    min, EXPAND, LINE_WIDTH, color, false);
            return;
        }

        RenderUtils.renderAreaOutlineNoCorners(
                min, max, LINE_WIDTH, color, color, color);
        RenderUtils.renderBlockOutline(
                min, EXPAND, LINE_WIDTH, color, false);
        RenderUtils.renderBlockOutline(
                max, EXPAND, LINE_WIDTH, color, false);

        if (Configs.Visuals.RENDER_PLACEMENT_BOX_SIDES.getBooleanValue())
        {
            float alpha = (float) Configs.Visuals.PLACEMENT_BOX_SIDE_ALPHA
                    .getDoubleValue();
            RenderUtils.renderAreaSides(
                    min, max, new Color4f(color.r, color.g, color.b, alpha));
        }
    }

    private static Color4f color(BoundsStatus status)
    {
        return switch (status)
        {
            case CHANGED_HEAD -> CHANGED_HEAD;
            case CHANGED_WORKING -> CHANGED_WORKING;
            case ADDED -> ADDED;
            case REMOVED -> REMOVED;
        };
    }

    private static Color4f color(int rgb)
    {
        return new Color4f(
                (rgb >> 16 & 0xFF) / 255f,
                (rgb >> 8 & 0xFF) / 255f,
                (rgb & 0xFF) / 255f);
    }
}
