package me.arnavpmr.lvc.overlay;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import me.arnavpmr.lvc.LvcDiagnostics;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff;
import me.arnavpmr.lvc.task.LvcOperationHandle;
import me.arnavpmr.lvc.task.LvcSubRegionStructuralDiffRestoreTask;
import me.arnavpmr.lvc.task.LvcTaskCallbacks;
import me.arnavpmr.lvc.task.LvcTaskRegistry;
import me.arnavpmr.lvc.task.LvcTaskScheduling;

/** Rebinds runtime structural diffs after Litematica restores its placements. */
public final class LvcPersistedStructuralDiffRestorer
{
    private static final Map<Path, SchematicPlacement> inspectedPlacements =
            new HashMap<>();

    private LvcPersistedStructuralDiffRestorer()
    {
    }

    public static synchronized void clear()
    {
        inspectedPlacements.clear();
    }

    public static synchronized void onClientTick(Minecraft minecraft)
    {
        ClientLevel clientLevel = minecraft.level;

        if (clientLevel == null)
        {
            return;
        }

        List<SchematicPlacement> livePlacements =
                DataManager.getSchematicPlacementManager()
                        .getAllSchematicsPlacements();
        inspectedPlacements.entrySet().removeIf(
                entry -> !livePlacements.contains(entry.getValue()));

        if (LvcTaskRegistry.hasActiveOperation())
        {
            return;
        }

        for (SchematicPlacement placement : livePlacements)
        {
            if (scheduleIfNeeded(clientLevel, placement))
            {
                return;
            }
        }
    }

    private static boolean scheduleIfNeeded(
            ClientLevel clientLevel,
            SchematicPlacement placement)
    {
        Path repositoryDirectory =
                LvcTrackingOverlayService.semanticTrackingRepositoryDirectory(
                        placement.getSchematicFile());

        if (repositoryDirectory == null)
        {
            return false;
        }

        Path key = repositoryDirectory.toAbsolutePath().normalize();

        if (inspectedPlacements.get(key) == placement ||
                LvcSubRegionStructuralDiffRegistry.isBound(key))
        {
            return false;
        }

        try
        {
            if (LvcTrackingOverlayService.trackingOverlayRevision(key, placement) !=
                    LvcTrackingOverlayRevision.CURRENT ||
                    !LvcTrackingOverlayService.isSemanticTrackingCacheCurrent(key) ||
                    !LvcSubRegionStructuralDiffRegistry.requiresOverlayRebuild(key))
            {
                inspectedPlacements.put(key, placement);
                return false;
            }

            Optional<LvcOperationHandle> handle =
                    LvcTaskRegistry.tryAcquireBackground(
                            LvcSubRegionStructuralDiffRestoreTask.OPERATION_NAME,
                            key);

            if (handle.isEmpty())
            {
                return false;
            }

            LvcOperationHandle operation = handle.get();

            try
            {
                LvcSubRegionStructuralDiffRestoreTask task =
                        new LvcSubRegionStructuralDiffRestoreTask(
                                operation,
                                key,
                                placement,
                                clientLevel,
                                callbacks(key));
                LvcTaskScheduling.scheduleForWorld(clientLevel, task);
            }
            catch (RuntimeException | LinkageError e)
            {
                LvcTaskRegistry.release(operation);
                throw e;
            }

            inspectedPlacements.put(key, placement);
            LvcDiagnostics.debug(
                    "scheduled persisted subregion diff restore repo='{}' placement='{}'",
                    key,
                    placement.getName());
            return true;
        }
        catch (Exception | LinkageError e)
        {
            inspectedPlacements.put(key, placement);
            LvcDiagnostics.warn(
                    "Failed to inspect persisted subregion diffs repo='{}' error='{}'",
                    key,
                    e.getMessage());
            return false;
        }
    }

    private static LvcTaskCallbacks<LvcSubRegionStructuralDiff> callbacks(
            Path repositoryDirectory)
    {
        return LvcTaskCallbacks.of(
                diff -> LvcDiagnostics.debug(
                        "restored persisted subregion diffs repo='{}' bounds={} retiredBlocks={}",
                        repositoryDirectory,
                        diff.bounds().size(),
                        diff.retiredBlocks().size()),
                failure -> LvcDiagnostics.warn(
                        "Failed to restore persisted subregion diffs repo='{}' error='{}'",
                        repositoryDirectory,
                        failure.getMessage()),
                () -> LvcDiagnostics.debug(
                        "persisted subregion diff restore aborted repo='{}'",
                        repositoryDirectory));
    }
}
