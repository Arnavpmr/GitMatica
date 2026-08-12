package me.arnavpmr.lvc.overlay;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import me.arnavpmr.lvc.LvcDiagnostics;
import me.arnavpmr.lvc.config.LvcConfigs;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.storage.LvcRepository;
import me.arnavpmr.lvc.storage.LvcSemanticRepository;

/** Caches working and HEAD manual origins for loaded tracking placements. */
public final class LvcManualOriginMarkerRegistry
{
    private static final Map<Path, CachedMarker> markers = new HashMap<>();

    private LvcManualOriginMarkerRegistry()
    {
    }

    public static synchronized void clear()
    {
        markers.clear();
    }

    public static synchronized void refreshAllLoaded()
    {
        markers.clear();
        List<SchematicPlacement> live = DataManager.getSchematicPlacementManager()
                .getAllSchematicsPlacements();

        for (SchematicPlacement placement : live)
        {
            Path repositoryDirectory = repositoryDirectory(placement);

            if (repositoryDirectory != null &&
                    LvcConfigs.isManualOriginVisible(repositoryDirectory))
            {
                inspect(repositoryDirectory, placement);
            }
        }
    }

    public static synchronized void refresh(Path repositoryDirectory)
    {
        Path key = key(repositoryDirectory);
        SchematicPlacement placement =
                LvcTrackingOverlayService.findTrackingPlacement(key);

        if (placement == null ||
                !LvcConfigs.isManualOriginVisible(repositoryDirectory))
        {
            markers.remove(key);
            return;
        }

        inspect(key, placement);
    }

    static synchronized void track(
            Path repositoryDirectory,
            SchematicPlacement placement)
    {
        Path key = key(repositoryDirectory);
        Path placementRepository = repositoryDirectory(placement);

        if (!key.equals(placementRepository) ||
                !LvcConfigs.isManualOriginVisible(key))
        {
            markers.remove(key);
            return;
        }

        inspect(key, placement);
    }

    static synchronized void remove(Path repositoryDirectory)
    {
        markers.remove(key(repositoryDirectory));
    }

    public static synchronized List<Marker> visibleMarkers()
    {
        List<Marker> result = new ArrayList<>();

        for (CachedMarker marker : markers.values())
        {
            if (!marker.placement().isEnabled() ||
                    !LvcConfigs.isManualOriginVisible(marker.repositoryDirectory()))
            {
                continue;
            }

            BlockPos working = worldPosition(
                    marker.placement().getOrigin(), marker.workingOrigin());
            BlockPos head = marker.headOrigin() != null
                    ? worldPosition(marker.placement().getOrigin(), marker.headOrigin())
                    : null;

            if (working != null)
            {
                result.add(new Marker(
                        marker.repositoryDirectory(), marker.placement(),
                        working, head));
            }
        }

        return List.copyOf(result);
    }

    public static synchronized boolean isModified(
            @Nullable SchematicPlacement placement)
    {
        CachedMarker marker = markerFor(placement);
        return marker != null && marker.headOrigin() != null &&
                !marker.workingOrigin().equals(marker.headOrigin());
    }

    @Nullable
    public static synchronized BlockPos workingWorldOrigin(
            @Nullable SchematicPlacement placement)
    {
        CachedMarker marker = markerFor(placement);
        return marker != null
                ? worldPosition(
                        marker.placement().getOrigin(), marker.workingOrigin())
                : null;
    }

    public static synchronized boolean shouldSuppressPlacementOrigin(
            BlockPos position)
    {
        for (Marker marker : visibleMarkers())
        {
            if (marker.working().equals(position) ||
                    position.equals(marker.head()))
            {
                return true;
            }
        }

        return false;
    }

    private static void inspect(
            Path repositoryDirectory,
            SchematicPlacement placement)
    {
        Path key = key(repositoryDirectory);

        try
        {
            ObjectId head = LvcRepository.resolveHead(key);
            LvcManifest workingManifest = LvcSemanticRepository.readManifest(key);
            String siteId = LvcSemanticRepository.defaultSiteId(workingManifest);
            BlockPos workingOrigin = position(
                    workingManifest.site(siteId).manualOrigin());
            BlockPos headOrigin = head != null
                    ? readHeadOrigin(key, head, siteId)
                    : null;
            markers.put(key, new CachedMarker(
                    key, placement, workingOrigin, headOrigin));
        }
        catch (Exception e)
        {
            markers.remove(key);
            LvcDiagnostics.debug(
                    "Failed to refresh manual-origin marker repo='{}' error='{}'",
                    key,
                    e.getMessage());
        }
    }

    private static BlockPos readHeadOrigin(
            Path repositoryDirectory,
            ObjectId head,
            String siteId) throws Exception
    {
        try (Git git = Git.open(repositoryDirectory.toFile());
             RevWalk walk = new RevWalk(git.getRepository()))
        {
            RevCommit commit = walk.parseCommit(head);
            LvcManifest manifest = LvcSemanticRepository.readCommitManifest(
                    git.getRepository(), commit);
            return position(manifest.site(siteId).manualOrigin());
        }
    }

    @Nullable
    private static CachedMarker markerFor(@Nullable SchematicPlacement placement)
    {
        if (placement == null)
        {
            return null;
        }

        for (CachedMarker marker : markers.values())
        {
            if (marker.placement() == placement)
            {
                return marker;
            }
        }

        return null;
    }

    @Nullable
    private static Path repositoryDirectory(SchematicPlacement placement)
    {
        Path repositoryDirectory =
                LvcTrackingOverlayService.semanticTrackingRepositoryDirectory(
                        placement.getSchematicFile());

        if (repositoryDirectory == null ||
                LvcTrackingOverlayService.trackingOverlayRevision(
                        repositoryDirectory, placement) !=
                        LvcTrackingOverlayRevision.CURRENT)
        {
            return null;
        }

        return key(repositoryDirectory);
    }

    private static BlockPos position(List<Integer> values)
    {
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    @Nullable
    private static BlockPos worldPosition(BlockPos placement, BlockPos relative)
    {
        try
        {
            return new BlockPos(
                    Math.addExact(placement.getX(), relative.getX()),
                    Math.addExact(placement.getY(), relative.getY()),
                    Math.addExact(placement.getZ(), relative.getZ()));
        }
        catch (ArithmeticException e)
        {
            return null;
        }
    }

    private static Path key(Path repositoryDirectory)
    {
        return repositoryDirectory.toAbsolutePath().normalize();
    }

    public record Marker(
            Path repositoryDirectory,
            SchematicPlacement placement,
            BlockPos working,
            @Nullable BlockPos head)
    {
        public boolean modified()
        {
            return this.head != null && !this.working.equals(this.head);
        }
    }

    private record CachedMarker(
            Path repositoryDirectory,
            SchematicPlacement placement,
            BlockPos workingOrigin,
            @Nullable BlockPos headOrigin)
    {
    }
}
