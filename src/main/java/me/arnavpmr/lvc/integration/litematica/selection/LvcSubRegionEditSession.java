package me.arnavpmr.lvc.integration.litematica.selection;

import java.nio.file.Path;
import java.util.List;
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
import me.arnavpmr.lvc.config.LvcConfigs;
import me.arnavpmr.lvc.gui.LvcGuiMessages;
import me.arnavpmr.lvc.gui.LvcSubRegionEditorWorkflow;
import me.arnavpmr.lvc.integration.litematica.tool.LvcToolModes;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.overlay.LvcTrackingOverlayRevision;
import me.arnavpmr.lvc.overlay.LvcManualOriginMarkerRegistry;
import me.arnavpmr.lvc.overlay.LvcTrackingOverlayService;
import me.arnavpmr.lvc.overlay.LvcTrackingSubRegionSelection;
import me.arnavpmr.lvc.semantic.LvcProjectEditorState;
import me.arnavpmr.lvc.semantic.LvcSemanticProjectEditor;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
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
        if (!LvcToolModes.isEditProjectActive())
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

        if (activeEdit != null && activeEdit.matches(overlay))
        {
            if (activeEdit.target() == EditTarget.MANUAL_ORIGIN)
            {
                return LvcConfigs.isManualOriginVisible(
                        overlay.repositoryDirectory())
                        ? activeEdit.selection()
                        : null;
            }

            if (Objects.equals(
                    activeEdit.regionName(),
                    overlay.placement().getSelectedSubRegionName()))
            {
                return activeEdit.selection();
            }
        }

        activeEdit = null;
        String regionName = overlay.placement().getSelectedSubRegionName();

        if (regionName == null)
        {
            return null;
        }

        return createRegionSelection(overlay, regionName);
    }

    public static boolean applyCurrentEdit()
    {
        AreaSelection selection = currentSelection();
        ActiveEdit edit = activeEdit;

        if (selection == null || edit == null)
        {
            return false;
        }

        try
        {
            if (edit.target() == EditTarget.MANUAL_ORIGIN)
            {
                return applyManualOrigin(edit, selection);
            }

            LvcTransientSubRegionSelection.Bounds bounds =
                    LvcTransientSubRegionSelection.relativeBounds(
                            selection,
                            edit.placement().getOrigin()
                    );
            return LvcSubRegionEditorWorkflow.applyRegionBounds(
                    edit.repositoryDirectory(),
                    edit.projectName(),
                    Objects.requireNonNull(edit.regionName()),
                    bounds.min(),
                    bounds.size()
            );
        }
        catch (Exception e)
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

    public static boolean selectOtherTargetAtCrosshair(int maxDistance)
    {
        if (!LvcToolModes.isEditProjectActive())
        {
            return false;
        }

        SelectedOverlay overlay = selectedOverlay();

        if (overlay == null)
        {
            clear();
            return false;
        }

        HitTarget target = findTarget(overlay, maxDistance);

        if (target == null || isFocused(overlay, target))
        {
            return false;
        }

        if (target.target() == EditTarget.MANUAL_ORIGIN)
        {
            focusManualOrigin(overlay);
        }
        else
        {
            focusRegion(overlay, Objects.requireNonNull(target.regionName()));
        }

        return true;
    }

    @Nullable
    public static BlockPos draftManualOrigin(Path repositoryDirectory)
    {
        Path key = repositoryDirectory.toAbsolutePath().normalize();

        if (activeEdit != null &&
                activeEdit.target() == EditTarget.MANUAL_ORIGIN &&
                activeEdit.repositoryDirectory().equals(key))
        {
            return activeEdit.selection().getExplicitOrigin();
        }

        return null;
    }

    public static void discardManualOriginDraft(Path repositoryDirectory)
    {
        if (activeEdit != null &&
                activeEdit.target() == EditTarget.MANUAL_ORIGIN &&
                activeEdit.repositoryDirectory().equals(
                        repositoryDirectory.toAbsolutePath().normalize()))
        {
            clear();
        }
    }

    public static boolean canApplyCurrentEdit()
    {
        if (Hotkeys.EXECUTE_OPERATION.getKeybind().getKeys().isEmpty())
        {
            return false;
        }

        if (!Configs.Generic.EXECUTE_REQUIRE_TOOL.getBooleanValue())
        {
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();
        return Configs.Generic.TOOL_ITEM_ENABLED.getBooleanValue() &&
                minecraft.player != null &&
                fi.dy.masa.litematica.util.EntityUtils.hasToolItem(minecraft.player);
    }

    @Nullable
    private static AreaSelection createRegionSelection(
            SelectedOverlay overlay,
            String regionName)
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
            activeEdit = new ActiveEdit(
                    overlay.repositoryDirectory(),
                    overlay.placement(),
                    state.projectName(),
                    EditTarget.SUB_REGION,
                    region.name(),
                    selection
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
    private static AreaSelection focusManualOrigin(SelectedOverlay overlay)
    {
        try
        {
            if (activeEdit != null && activeEdit.matches(overlay) &&
                    activeEdit.target() == EditTarget.MANUAL_ORIGIN)
            {
                return activeEdit.selection();
            }

            LvcProjectEditorState state =
                    LvcSemanticProjectEditor.readState(
                            overlay.repositoryDirectory());
            AreaSelection selection = new AreaSelection();
            selection.setName(state.projectName() + " / Manual Origin");
            selection.setExplicitOrigin(state.manualOrigin());
            selection.setOriginSelected(true);
            overlay.placement().setSelectedSubRegionName(null);
            LvcTrackingSubRegionSelection.set(
                    overlay.repositoryDirectory(), null);
            activeEdit = new ActiveEdit(
                    overlay.repositoryDirectory(),
                    overlay.placement(),
                    state.projectName(),
                    EditTarget.MANUAL_ORIGIN,
                    null,
                    selection);
            return selection;
        }
        catch (Exception e)
        {
            clear();
            LvcDiagnostics.debug(
                    "Failed to focus manual origin repo='{}' error='{}'",
                    overlay.repositoryDirectory(),
                    e.getMessage());
            return null;
        }
    }

    private static void focusRegion(SelectedOverlay overlay, String regionName)
    {
        clear();
        LvcTrackingSubRegionSelection.set(
                overlay.repositoryDirectory(), regionName);
        createRegionSelection(overlay, regionName);
    }

    private static boolean applyManualOrigin(
            ActiveEdit edit,
            AreaSelection selection) throws Exception
    {
        BlockPos worldOrigin = selection.getExplicitOrigin();

        if (worldOrigin == null)
        {
            throw new IllegalStateException(
                    "Gitmatica manual origin selection has no origin");
        }

        LvcSemanticProjectEditor.updateManualOriginFromWorld(
                edit.repositoryDirectory(), worldOrigin);
        Path repositoryDirectory = edit.repositoryDirectory();
        clear();
        LvcManualOriginMarkerRegistry.refresh(repositoryDirectory);
        LvcGuiMessages.show(
                MessageType.SUCCESS,
                "gitmatica.message.lvc_project_editor.manual_origin_updated");
        return true;
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
    private static HitTarget findTarget(
            SelectedOverlay overlay,
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
                minecraft.level, entity, false, maxDistance);
        double vanillaDistance = vanillaHit.getType() == HitResult.Type.MISS
                ? -1D
                : vanillaHit.getLocation().distanceTo(start);
        BlockPos manualOrigin = null;

        if (LvcConfigs.isManualOriginVisible(overlay.repositoryDirectory()))
        {
            try
            {
                manualOrigin = activeEdit != null &&
                        activeEdit.matches(overlay) &&
                        activeEdit.target() == EditTarget.MANUAL_ORIGIN
                        ? activeEdit.selection().getExplicitOrigin()
                        : LvcSemanticProjectEditor.readState(
                                overlay.repositoryDirectory()).manualOrigin();
            }
            catch (Exception ignored)
            {
            }
        }

        FocusedTarget focused = focusedTarget(overlay);
        return findTarget(
                manualOrigin,
                overlay.placement().getSubRegionBoxes(RequiredEnabled.ANY),
                focused != null ? focused.target() : null,
                focused != null ? focused.regionName() : null,
                start,
                end,
                vanillaDistance);
    }

    @Nullable
    static HitTarget findTarget(
            @Nullable BlockPos manualOrigin,
            Map<String, Box> subRegionBoxes,
            @Nullable EditTarget focusedTarget,
            @Nullable String focusedRegionName,
            Vec3 start,
            Vec3 end,
            double vanillaDistance)
    {
        List<HitTarget> candidates = new java.util.ArrayList<>();
        addHit(candidates, EditTarget.MANUAL_ORIGIN, null, manualOrigin,
                start, end, vanillaDistance);

        for (Map.Entry<String, Box> entry : subRegionBoxes.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList())
        {
            addHit(candidates, EditTarget.SUB_REGION, entry.getKey(),
                    entry.getValue().getPos1(), start, end, vanillaDistance);
            addHit(candidates, EditTarget.SUB_REGION, entry.getKey(),
                    entry.getValue().getPos2(), start, end, vanillaDistance);
        }

        return candidates.stream()
                .sorted(java.util.Comparator
                        .comparingDouble(HitTarget::distance)
                        .thenComparingInt(target -> priority(
                                target, focusedTarget, focusedRegionName))
                        .thenComparing(target -> target.regionName() == null
                                ? ""
                                : target.regionName()))
                .findFirst()
                .orElse(null);
    }

    private static void addHit(
            List<HitTarget> candidates,
            EditTarget target,
            @Nullable String regionName,
            @Nullable BlockPos position,
            Vec3 start,
            Vec3 end,
            double vanillaDistance)
    {
        if (position == null)
        {
            return;
        }

        Optional<Vec3> hit = PositionUtils.createAABBForPosition(position)
                .clip(start, end);

        if (hit.isEmpty())
        {
            return;
        }

        double distance = hit.get().distanceTo(start);

        if (vanillaDistance < 0D || distance <= vanillaDistance)
        {
            candidates.add(new HitTarget(target, regionName, distance));
        }
    }

    private static int priority(
            HitTarget target,
            @Nullable EditTarget focusedTarget,
            @Nullable String focusedRegionName)
    {
        if (target.target() == focusedTarget &&
                Objects.equals(target.regionName(), focusedRegionName))
        {
            return 0;
        }

        return target.target() == EditTarget.MANUAL_ORIGIN ? 1 : 2;
    }

    private static boolean isFocused(SelectedOverlay overlay, HitTarget target)
    {
        FocusedTarget focused = focusedTarget(overlay);
        return focused != null && focused.target() == target.target() &&
                Objects.equals(focused.regionName(), target.regionName());
    }

    @Nullable
    private static FocusedTarget focusedTarget(SelectedOverlay overlay)
    {
        if (activeEdit != null && activeEdit.matches(overlay))
        {
            return new FocusedTarget(
                    activeEdit.target(), activeEdit.regionName());
        }

        String regionName = overlay.placement().getSelectedSubRegionName();
        return regionName != null
                ? new FocusedTarget(EditTarget.SUB_REGION, regionName)
                : null;
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
            EditTarget target,
            @Nullable String regionName,
            AreaSelection selection)
    {
        private boolean matches(SelectedOverlay overlay)
        {
            return this.placement == overlay.placement() &&
                    this.repositoryDirectory.equals(overlay.repositoryDirectory());
        }
    }

    enum EditTarget
    {
        SUB_REGION,
        MANUAL_ORIGIN
    }

    record HitTarget(
            EditTarget target,
            @Nullable String regionName,
            double distance)
    {
    }

    private record FocusedTarget(
            EditTarget target,
            @Nullable String regionName)
    {
    }
}
