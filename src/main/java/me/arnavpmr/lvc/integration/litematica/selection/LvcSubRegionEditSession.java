package me.arnavpmr.lvc.integration.litematica.selection;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import me.arnavpmr.lvc.LvcDiagnostics;
import me.arnavpmr.lvc.gui.LvcGuiMessages;
import me.arnavpmr.lvc.gui.LvcSubRegionEditorWorkflow;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.overlay.LvcTrackingOverlayRevision;
import me.arnavpmr.lvc.overlay.LvcTrackingOverlayService;
import me.arnavpmr.lvc.overlay.LvcTrackingSubRegionSelection;
import me.arnavpmr.lvc.semantic.LvcProjectEditorState;
import me.arnavpmr.lvc.semantic.LvcSemanticProjectEditor;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.tool.ToolMode;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.litematica.util.RayTraceUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;

public final class LvcSubRegionEditSession
{
    @Nullable private static ActiveEdit activeEdit;

    private LvcSubRegionEditSession()
    {
    }

    public static void onToolModeChanged(ToolMode mode)
    {
        clear();
    }

    public static void clear()
    {
        activeEdit = null;
    }

    @Nullable
    public static AreaSelection currentSelection()
    {
        if (!LvcToolModes.isEditSubregionsActive())
        {
            clear();
            return null;
        }

        SelectedOverlay overlay = selectedOverlay();

        if (overlay == null)
        {
            clear();
            return null;
        }

        String regionName = overlay.placement().getSelectedSubRegionName();

        if (regionName == null)
        {
            clear();
            return null;
        }

        if (activeEdit != null && activeEdit.matches(overlay, regionName))
        {
            return activeEdit.selection();
        }

        return createSelection(overlay, regionName);
    }

    public static boolean applyCurrentBounds()
    {
        AreaSelection selection = currentSelection();
        ActiveEdit edit = activeEdit;

        if (selection == null || edit == null)
        {
            return false;
        }

        try
        {
            LvcTransientSubRegionSelection.Bounds bounds =
                    LvcTransientSubRegionSelection.relativeBounds(
                            selection,
                            edit.placement().getOrigin()
                    );
            return LvcSubRegionEditorWorkflow.applyRegionBounds(
                    edit.repositoryDirectory(),
                    edit.projectName(),
                    edit.regionName(),
                    bounds.min(),
                    bounds.size()
            );
        }
        catch (RuntimeException e)
        {
            LvcDiagnostics.warn(
                    "Failed to apply transient subregion selection repo='{}' region='{}' error='{}'",
                    edit.repositoryDirectory(),
                    edit.regionName(),
                    e.getMessage()
            );
            LvcGuiMessages.show(
                    MessageType.ERROR,
                    "gitmatica.error.lvc_project_editor.save_failed",
                    e.getMessage()
            );
            return true;
        }
    }

    /**
     * Returns whether the transient box differs from the bounds last applied to
     * the working manifest. Rendering uses this to keep native selection
     * styling for a draft without obscuring an applied structural status.
     */
    public static boolean isCurrentDraftPending()
    {
        ActiveEdit edit = activeEdit;

        if (edit == null || !LvcToolModes.isEditSubregionsActive())
        {
            return false;
        }

        try
        {
            LvcTransientSubRegionSelection.Bounds current =
                    LvcTransientSubRegionSelection.relativeBounds(
                            edit.selection(), edit.placement().getOrigin());
            return !current.equals(edit.appliedBounds());
        }
        catch (RuntimeException ignored)
        {
            return true;
        }
    }

    public static boolean selectOtherSubRegionAtCrosshair(int maxDistance)
    {
        if (!LvcToolModes.isEditSubregionsActive())
        {
            return false;
        }

        SelectedOverlay overlay = selectedOverlay();

        if (overlay == null)
        {
            clear();
            return false;
        }

        String selectedRegion = overlay.placement().getSelectedSubRegionName();
        String hitRegion = findSubRegionCorner(
                overlay.placement(),
                maxDistance
        );

        if (hitRegion == null || Objects.equals(hitRegion, selectedRegion))
        {
            return false;
        }

        clear();
        LvcTrackingSubRegionSelection.set(overlay.repositoryDirectory(), hitRegion);
        createSelection(overlay, hitRegion);
        return true;
    }

    @Nullable
    private static AreaSelection createSelection(SelectedOverlay overlay, String regionName)
    {
        try
        {
            LvcProjectEditorState state =
                    LvcSemanticProjectEditor.readState(overlay.repositoryDirectory());
            LvcManifest.Region region = state.regions().stream()
                    .filter(candidate -> candidate.name().equals(regionName))
                    .findFirst()
                    .orElse(null);

            if (region == null)
            {
                clear();
                return null;
            }

            AreaSelection selection = LvcTransientSubRegionSelection.create(
                    state.projectName(),
                    region,
                    overlay.placement().getOrigin()
            );
            LvcTransientSubRegionSelection.Bounds appliedBounds =
                    LvcTransientSubRegionSelection.relativeBounds(
                            selection, overlay.placement().getOrigin());
            activeEdit = new ActiveEdit(
                    overlay.repositoryDirectory(),
                    overlay.placement(),
                    state.projectName(),
                    region.name(),
                    selection,
                    appliedBounds
            );
            return selection;
        }
        catch (Exception e)
        {
            clear();
            LvcDiagnostics.debug(
                    "Failed to create transient subregion selection repo='{}' region='{}' error='{}'",
                    overlay.repositoryDirectory(),
                    regionName,
                    e.getMessage()
            );
            return null;
        }
    }

    @Nullable
    private static SelectedOverlay selectedOverlay()
    {
        SchematicPlacement placement =
                DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();

        if (placement == null)
        {
            return null;
        }

        Path repositoryDirectory =
                LvcTrackingOverlayService.semanticTrackingRepositoryDirectory(
                        placement.getSchematicFile()
                );

        if (repositoryDirectory == null ||
                LvcTrackingOverlayService.trackingOverlayRevision(
                        repositoryDirectory,
                        placement
                ) != LvcTrackingOverlayRevision.CURRENT)
        {
            return null;
        }

        return new SelectedOverlay(repositoryDirectory.toAbsolutePath().normalize(), placement);
    }

    @Nullable
    private static String findSubRegionCorner(
            SchematicPlacement placement,
            int maxDistance)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = fi.dy.masa.malilib.util.EntityUtils.getCameraEntity();

        if (minecraft.level == null || entity == null)
        {
            return null;
        }

        Vec3 start = entity.getEyePosition(1f);
        Vec3 end = start.add(entity.getViewVector(1f).scale(maxDistance));
        HitResult vanillaHit = RayTraceUtils.getRayTraceFromEntity(
                minecraft.level,
                entity,
                false,
                maxDistance
        );
        double vanillaDistance = vanillaHit.getType() == HitResult.Type.MISS ?
                -1D : vanillaHit.getLocation().distanceTo(start);
        return findSubRegionCorner(
                placement.getSubRegionBoxes(RequiredEnabled.ANY),
                start,
                end,
                vanillaDistance
        );
    }

    @Nullable
    static String findSubRegionCorner(
            Map<String, Box> subRegionBoxes,
            Vec3 start,
            Vec3 end,
            double vanillaDistance)
    {
        double closestDistance = -1D;
        String closestRegion = null;

        for (Map.Entry<String, Box> entry :
                subRegionBoxes.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList())
        {
            Box box = entry.getValue();

            for (BlockPos corner : new BlockPos[] { box.getPos1(), box.getPos2() })
            {
                if (corner == null)
                {
                    continue;
                }

                Optional<Vec3> hit = PositionUtils.createAABBForPosition(corner).clip(start, end);

                if (hit.isEmpty())
                {
                    continue;
                }

                double distance = hit.get().distanceTo(start);

                if ((vanillaDistance < 0D || distance <= vanillaDistance) &&
                        (closestDistance < 0D || distance < closestDistance))
                {
                    closestDistance = distance;
                    closestRegion = entry.getKey();
                }
            }
        }

        return closestRegion;
    }

    private record SelectedOverlay(Path repositoryDirectory, SchematicPlacement placement)
    {
    }

    private record ActiveEdit(
            Path repositoryDirectory,
            SchematicPlacement placement,
            String projectName,
            String regionName,
            AreaSelection selection,
            LvcTransientSubRegionSelection.Bounds appliedBounds)
    {
        private boolean matches(SelectedOverlay overlay, String selectedRegion)
        {
            return this.placement == overlay.placement() &&
                    this.repositoryDirectory.equals(overlay.repositoryDirectory()) &&
                    this.regionName.equals(selectedRegion);
        }
    }
}
