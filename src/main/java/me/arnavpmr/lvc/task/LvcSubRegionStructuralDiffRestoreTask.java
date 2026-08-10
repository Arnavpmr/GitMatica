package me.arnavpmr.lvc.task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;

import org.eclipse.jgit.lib.ObjectId;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiffLoader;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.overlay.LvcSubRegionStructuralDiffRegistry;
import me.arnavpmr.lvc.overlay.LvcTrackingOverlayRevision;
import me.arnavpmr.lvc.overlay.LvcTrackingOverlayService;
import me.arnavpmr.lvc.storage.LvcRepository;
import me.arnavpmr.lvc.storage.LvcSemanticRepository;

/** Restores runtime-only structural diff state for a persisted placement. */
public final class LvcSubRegionStructuralDiffRestoreTask
        extends LvcChunkedTaskBase<LvcSubRegionStructuralDiff>
{
    public static final String OPERATION_NAME = "LVC Restore Subregion Diffs";
    private static final long RESTORE_BUDGET_NANOS = 6_000_000L;

    private final Path repositoryDirectory;
    private final SchematicPlacement placement;
    private final ClientLevel clientLevel;
    @Nullable private LvcSubRegionStructuralDiffLoader loader;
    private LvcSubRegionStructuralDiff restoredDiff =
            LvcSubRegionStructuralDiff.empty();

    public LvcSubRegionStructuralDiffRestoreTask(
            LvcOperationHandle handle,
            Path repositoryDirectory,
            SchematicPlacement placement,
            ClientLevel clientLevel,
            LvcTaskCallbacks<LvcSubRegionStructuralDiff> callbacks)
    {
        super(handle, OPERATION_NAME, callbacks, true, RESTORE_BUDGET_NANOS);
        this.repositoryDirectory = Objects.requireNonNull(repositoryDirectory,
                "repositoryDirectory").toAbsolutePath().normalize();
        this.placement = Objects.requireNonNull(placement, "placement");
        this.clientLevel = Objects.requireNonNull(clientLevel, "clientLevel");
    }

    @Override
    public void init()
    {
        try
        {
            this.loader = LvcSubRegionStructuralDiffLoader.open(
                    this.repositoryDirectory);
        }
        catch (Exception e)
        {
            this.fail(e);
        }
    }

    @Override
    protected boolean step() throws Exception
    {
        LvcSubRegionStructuralDiffLoader activeLoader =
                Objects.requireNonNull(this.loader, "loader");

        if (!activeLoader.isComplete())
        {
            activeLoader.processNextChunk();
            return false;
        }

        this.validateStillCurrent(activeLoader);
        this.restoredDiff = activeLoader.result();
        LvcSubRegionStructuralDiffRegistry.bind(
                this.repositoryDirectory,
                this.placement,
                this.restoredDiff);
        return true;
    }

    @Override
    protected LvcSubRegionStructuralDiff result()
    {
        return this.restoredDiff;
    }

    @Override
    public void stop()
    {
        try
        {
            super.stop();
        }
        finally
        {
            if (this.loader != null)
            {
                this.loader.close();
                this.loader = null;
            }
        }
    }

    private void validateStillCurrent(
            LvcSubRegionStructuralDiffLoader activeLoader) throws IOException
    {
        if (this.clientLevel != Minecraft.getInstance().level ||
                !DataManager.getSchematicPlacementManager()
                        .getAllSchematicsPlacements().contains(this.placement))
        {
            throw new IOException(
                    "Persisted Gitmatica placement changed while restoring subregion diffs");
        }

        Path placementRepository =
                LvcTrackingOverlayService.semanticTrackingRepositoryDirectory(
                        this.placement.getSchematicFile());

        if (!this.repositoryDirectory.equals(placementRepository) ||
                LvcTrackingOverlayService.trackingOverlayRevision(
                        this.repositoryDirectory,
                        this.placement) != LvcTrackingOverlayRevision.CURRENT)
        {
            throw new IOException(
                    "Persisted Gitmatica placement is no longer the current revision");
        }

        ObjectId head = LvcRepository.resolveHead(this.repositoryDirectory);

        if (head == null || !head.getName().equals(activeLoader.headCommitId()))
        {
            throw new IOException(
                    "Gitmatica HEAD changed while restoring subregion diffs");
        }

        LvcManifest working = LvcSemanticRepository.readManifest(
                this.repositoryDirectory);
        String definitionId =
                LvcSemanticRepository.trackingOverlayDefinitionId(working);

        if (!definitionId.equals(activeLoader.workingDefinitionId()))
        {
            throw new IOException(
                    "Gitmatica subregion definitions changed while restoring diffs");
        }
    }
}
