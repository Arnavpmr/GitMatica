package me.arnavpmr.lvc.integration.litematica.selection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import me.arnavpmr.lvc.model.LvcManifest;

import fi.dy.masa.litematica.selection.Box;

public final class LvcTransientSubRegionSelectionIntegrationTest
{
    private LvcTransientSubRegionSelectionIntegrationTest()
    {
    }

    public static void runAll()
    {
        run("transient selection preserves project-relative bounds",
                LvcTransientSubRegionSelectionIntegrationTest::preservesRelativeBounds);
        run("shared subregion corners select the alphabetically first name",
                LvcTransientSubRegionSelectionIntegrationTest::sharedCornerUsesAlphabeticalName);
        run("manual origin wins a shared unfocused subregion corner",
                LvcTransientSubRegionSelectionIntegrationTest::manualOriginWinsUnfocusedSharedCorner);
        run("focused subregion wins its corner over the manual origin",
                LvcTransientSubRegionSelectionIntegrationTest::focusedRegionWinsSharedCorner);
    }

    private static void preservesRelativeBounds()
    {
        BlockPos placementOrigin = new BlockPos(100, 64, -20);
        LvcManifest.Region region = new LvcManifest.Region(
                "Storage",
                List.of(2, 3, 4),
                List.of(5, 6, 7)
        );
        LvcTransientSubRegionSelection.WorldBounds worldBounds =
                LvcTransientSubRegionSelection.worldBounds(region, placementOrigin);
        assertEquals(new BlockPos(102, 67, -16), worldBounds.min(), "world minimum");
        assertEquals(new BlockPos(106, 72, -10), worldBounds.max(), "world maximum");

        LvcTransientSubRegionSelection.Bounds bounds =
                LvcTransientSubRegionSelection.relativeBounds(
                        worldBounds.max(),
                        worldBounds.min(),
                        placementOrigin
                );

        assertEquals(new BlockPos(2, 3, 4), bounds.min(), "normalized relative minimum");
        assertEquals(new BlockPos(5, 6, 7), bounds.size(), "normalized size");
    }

    private static void sharedCornerUsesAlphabeticalName()
    {
        BlockPos sharedCorner = BlockPos.ZERO;
        Map<String, Box> boxes = new LinkedHashMap<>();
        boxes.put("Zulu", new Box(sharedCorner, new BlockPos(4, 4, 4), "Zulu"));
        boxes.put("Alpha", new Box(sharedCorner, new BlockPos(-4, -4, -4), "Alpha"));

        Vec3 start = new Vec3(0.5D, 0.5D, -2D);
        Vec3 end = new Vec3(0.5D, 0.5D, 2D);
        String hit = LvcSubRegionEditSession.findSubRegionCorner(
                boxes,
                start,
                end,
                -1D
        );
        assertEquals("Alpha", hit, "alphabetical shared-corner selection");
    }

    private static void manualOriginWinsUnfocusedSharedCorner()
    {
        LvcSubRegionEditSession.HitTarget hit = sharedManualOriginHit(
                null, null);
        assertEquals(
                LvcSubRegionEditSession.EditTarget.MANUAL_ORIGIN,
                hit.target(),
                "unfocused shared-corner target");
    }

    private static void focusedRegionWinsSharedCorner()
    {
        LvcSubRegionEditSession.HitTarget hit = sharedManualOriginHit(
                LvcSubRegionEditSession.EditTarget.SUB_REGION, "Alpha");
        assertEquals(
                LvcSubRegionEditSession.EditTarget.SUB_REGION,
                hit.target(),
                "focused shared-corner target");
        assertEquals("Alpha", hit.regionName(), "focused region name");
    }

    private static LvcSubRegionEditSession.HitTarget sharedManualOriginHit(
            LvcSubRegionEditSession.EditTarget focusedTarget,
            String focusedRegionName)
    {
        BlockPos sharedCorner = BlockPos.ZERO;
        Map<String, Box> boxes = Map.of(
                "Alpha",
                new Box(sharedCorner, new BlockPos(4, 4, 4), "Alpha"));
        Vec3 start = new Vec3(0.5D, 0.5D, -2D);
        Vec3 end = new Vec3(0.5D, 0.5D, 2D);
        return Objects.requireNonNull(LvcSubRegionEditSession.findTarget(
                sharedCorner,
                boxes,
                focusedTarget,
                focusedRegionName,
                start,
                end,
                -1D));
    }

    private static void run(String name, Runnable test)
    {
        try
        {
            test.run();
            System.out.println("[PASS] " + name);
        }
        catch (Throwable throwable)
        {
            System.err.println("[FAIL] " + name);
            throw throwable;
        }
    }

    private static void assertEquals(Object expected, Object actual, String message)
    {
        if (!Objects.equals(expected, actual))
        {
            throw new AssertionError(
                    message + " expected <" + expected + "> but was <" + actual + ">"
            );
        }
    }
}
