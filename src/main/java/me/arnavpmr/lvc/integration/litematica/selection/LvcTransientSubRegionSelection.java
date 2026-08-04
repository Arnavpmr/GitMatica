package me.arnavpmr.lvc.integration.litematica.selection;

import java.util.List;
import net.minecraft.core.BlockPos;
import me.arnavpmr.lvc.model.LvcManifest;

import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;

final class LvcTransientSubRegionSelection
{
    private LvcTransientSubRegionSelection()
    {
    }

    static AreaSelection create(
            String projectName,
            LvcManifest.Region region,
            BlockPos placementOrigin)
    {
        WorldBounds bounds = worldBounds(region, placementOrigin);
        AreaSelection selection = new AreaSelection();
        selection.setName(projectName + " / " + region.name());
        selection.addSubRegionBox(new Box(bounds.min(), bounds.max(), region.name()), false);
        selection.setSelectedSubRegionBox(region.name());
        return selection;
    }

    static WorldBounds worldBounds(LvcManifest.Region region, BlockPos placementOrigin)
    {
        BlockPos relativeMin = position(region.min());
        BlockPos size = position(region.size());
        BlockPos worldMin = placementOrigin.offset(relativeMin);
        BlockPos worldMax = worldMin.offset(
                size.getX() - 1,
                size.getY() - 1,
                size.getZ() - 1
        );
        return new WorldBounds(worldMin, worldMax);
    }

    static Bounds relativeBounds(AreaSelection selection, BlockPos placementOrigin)
    {
        List<Box> boxes = selection.getAllSubRegionBoxes();

        if (boxes.size() != 1)
        {
            throw new IllegalStateException("Gitmatica subregion edit selection must contain one box");
        }

        Box box = boxes.getFirst();
        BlockPos first = box.getPos1();
        BlockPos second = box.getPos2();

        if (first == null || second == null)
        {
            throw new IllegalStateException("Gitmatica subregion edit selection has an incomplete box");
        }

        return relativeBounds(first, second, placementOrigin);
    }

    static Bounds relativeBounds(BlockPos first, BlockPos second, BlockPos placementOrigin)
    {
        BlockPos worldMin = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())
        );
        BlockPos worldMax = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())
        );
        BlockPos size = worldMax.subtract(worldMin).offset(1, 1, 1);
        return new Bounds(worldMin.subtract(placementOrigin), size);
    }

    private static BlockPos position(List<Integer> values)
    {
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    record Bounds(BlockPos min, BlockPos size)
    {
    }

    record WorldBounds(BlockPos min, BlockPos max)
    {
    }
}
