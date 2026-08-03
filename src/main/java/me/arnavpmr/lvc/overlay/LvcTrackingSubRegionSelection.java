package me.arnavpmr.lvc.overlay;

import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

public final class LvcTrackingSubRegionSelection
{
    private LvcTrackingSubRegionSelection()
    {
    }

    @Nullable
    public static String get(Path repositoryDirectory)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        SchematicPlacement placement = LvcTrackingOverlayService.findTrackingPlacement(repositoryDirectory);

        if (placement != null)
        {
            String selected = placement.getSelectedSubRegionName();
            LvcTrackingOverlayRegistry.putSelectedSubRegion(repositoryDirectory, selected);
            return selected;
        }

        return LvcTrackingOverlayRegistry.selectedSubRegion(repositoryDirectory);
    }

    public static void set(Path repositoryDirectory, @Nullable String regionName)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        LvcTrackingOverlayRegistry.putSelectedSubRegion(repositoryDirectory, regionName);
        SchematicPlacement placement = LvcTrackingOverlayService.findTrackingPlacement(repositoryDirectory);

        if (placement != null)
        {
            placement.setSelectedSubRegionName(regionName);
        }
    }

    public static void clear(Path repositoryDirectory)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        LvcTrackingOverlayRegistry.removeSelectedSubRegion(repositoryDirectory);
    }
}
