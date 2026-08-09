package me.arnavpmr.lvc.overlay;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff.BoundsStatus;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiffLoader;
import me.arnavpmr.lvc.integration.litematica.verifier.GitmaticaVerifiers;
import me.arnavpmr.lvc.model.LvcIntPosition;

/**
 * Runtime binding between project-relative structural diffs and live
 * Litematica placements. Published entries are immutable so chunk compilation
 * can query them safely off-thread.
 */
public final class LvcSubRegionStructuralDiffRegistry
{
    private static volatile List<BoundDiff> entries = List.of();

    private LvcSubRegionStructuralDiffRegistry()
    {
    }

    public static synchronized void bind(
            Path repositoryDirectory,
            SchematicPlacement placement,
            LvcSubRegionStructuralDiff diff)
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(diff, "diff");
        Path key = key(repositoryDirectory);
        List<BoundDiff> updated = new ArrayList<>();
        Set<ChunkPos> changedChunks = new HashSet<>();

        for (BoundDiff entry : entries)
        {
            if (entry.repositoryDirectory().equals(key) || entry.placement() == placement)
            {
                changedChunks.addAll(entry.retiredChunks());
            }
            else
            {
                updated.add(entry);
            }
        }

        if (!diff.isEmpty())
        {
            BoundDiff bound = BoundDiff.create(key, placement, diff);
            updated.add(bound);
            changedChunks.addAll(bound.retiredChunks());
            applyVerifier(bound);
        }
        else
        {
            GitmaticaVerifiers.extension(placement.getSchematicVerifier())
                    .gitmatica$setStructuralMismatches(Map.of());
        }

        entries = List.copyOf(updated);
        rebuildChunks(changedChunks);
    }

    public static synchronized void removeRepository(Path repositoryDirectory)
    {
        Path key = key(repositoryDirectory);
        List<BoundDiff> updated = new ArrayList<>();
        Set<ChunkPos> changedChunks = new HashSet<>();

        for (BoundDiff entry : entries)
        {
            if (entry.repositoryDirectory().equals(key))
            {
                changedChunks.addAll(entry.retiredChunks());
            }
            else
            {
                updated.add(entry);
            }
        }

        entries = List.copyOf(updated);
        rebuildChunks(changedChunks);
    }

    public static synchronized void removePlacement(SchematicPlacement placement)
    {
        List<BoundDiff> updated = new ArrayList<>();
        Set<ChunkPos> changedChunks = new HashSet<>();

        for (BoundDiff entry : entries)
        {
            if (entry.placement() == placement)
            {
                changedChunks.addAll(entry.retiredChunks());
            }
            else
            {
                updated.add(entry);
            }
        }

        entries = List.copyOf(updated);
        rebuildChunks(changedChunks);
    }

    public static synchronized void clear()
    {
        entries = List.of();
    }

    public static boolean isBound(Path repositoryDirectory)
    {
        Path key = key(repositoryDirectory);

        for (BoundDiff entry : entries)
        {
            if (entry.repositoryDirectory().equals(key))
            {
                return true;
            }
        }

        return false;
    }

    public static boolean requiresOverlayRebuild(Path repositoryDirectory)
            throws IOException
    {
        return !isBound(repositoryDirectory) &&
                LvcSubRegionStructuralDiffLoader.hasStructuralBounds(
                        repositoryDirectory);
    }

    public static boolean hasRetiredBlocksInChunk(int chunkX, int chunkZ)
    {
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);

        for (BoundDiff entry : entries)
        {
            if (entry.active() && entry.retiredChunks().contains(chunk))
            {
                return true;
            }
        }

        return false;
    }

    public static List<IntBoundingBox> retiredBoxesInChunk(int chunkX, int chunkZ)
    {
        ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
        List<IntBoundingBox> result = new ArrayList<>();

        for (BoundDiff entry : entries)
        {
            if (entry.active())
            {
                result.addAll(entry.retiredBoxes().getOrDefault(chunk, List.of()));
            }
        }

        return List.copyOf(result);
    }

    @Nullable
    public static BlockState retiredStateAt(BlockPos position)
    {
        for (BoundDiff entry : entries)
        {
            if (entry.active())
            {
                BlockState state = entry.retiredBlocks().get(position);

                if (state != null)
                {
                    return state;
                }
            }
        }

        return null;
    }

    public static List<WorldBounds> selectedWorldBounds()
    {
        SchematicPlacement selected = DataManager.getSchematicPlacementManager()
                .getSelectedSchematicPlacement();
        BoundDiff entry = entryFor(selected);
        return entry != null && entry.active() ? entry.bounds() : List.of();
    }

    public static boolean isDirtyWorkingBox(
            @Nullable SchematicPlacement placement,
            Box box)
    {
        BoundDiff entry = entryFor(placement);

        if (entry == null || !entry.active() || box.getPos1() == null || box.getPos2() == null)
        {
            return false;
        }

        BlockPos boxMin = min(box.getPos1(), box.getPos2());
        BlockPos boxMax = max(box.getPos1(), box.getPos2());

        for (WorldBounds bounds : entry.bounds())
        {
            if ((bounds.status() == BoundsStatus.ADDED ||
                 bounds.status() == BoundsStatus.CHANGED_WORKING) &&
                bounds.min().equals(boxMin) && bounds.max().equals(boxMax))
            {
                return true;
            }
        }

        return false;
    }

    public static void reapplyVerifier(SchematicPlacement placement)
    {
        BoundDiff entry = entryFor(placement);

        if (entry != null)
        {
            applyVerifier(entry);
        }
    }

    public static synchronized void onClientTick(Minecraft minecraft)
    {
        if (minecraft.level == null || entries.isEmpty())
        {
            return;
        }

        List<SchematicPlacement> live = DataManager.getSchematicPlacementManager()
                .getAllSchematicsPlacements();
        List<BoundDiff> updated = new ArrayList<>();
        Set<ChunkPos> changedChunks = new HashSet<>();
        boolean changed = false;

        for (BoundDiff entry : entries)
        {
            if (!live.contains(entry.placement()))
            {
                changed = true;
                changedChunks.addAll(entry.retiredChunks());
                continue;
            }

            BlockPos origin = entry.placement().getOrigin();

            if (!entry.origin().equals(origin) ||
                    entry.enabled() != entry.placement().isEnabled())
            {
                BoundDiff refreshed = BoundDiff.create(
                        entry.repositoryDirectory(), entry.placement(), entry.source());
                changed = true;
                changedChunks.addAll(entry.retiredChunks());
                changedChunks.addAll(refreshed.retiredChunks());
                updated.add(refreshed);
                applyVerifier(refreshed);
            }
            else
            {
                updated.add(entry);
            }
        }

        if (changed)
        {
            entries = List.copyOf(updated);
            rebuildChunks(changedChunks);
        }
    }

    @Nullable
    private static BoundDiff entryFor(@Nullable SchematicPlacement placement)
    {
        if (placement == null)
        {
            return null;
        }

        for (BoundDiff entry : entries)
        {
            if (entry.placement() == placement)
            {
                return entry;
            }
        }

        return null;
    }

    private static void applyVerifier(BoundDiff entry)
    {
        GitmaticaVerifiers.extension(entry.placement().getSchematicVerifier())
                .gitmatica$setStructuralMismatches(
                        entry.active() ? entry.retiredBlocks() : Map.of());
    }

    private static void rebuildChunks(Set<ChunkPos> chunks)
    {
        if (chunks.isEmpty())
        {
            return;
        }

        for (ChunkPos chunk : chunks)
        {
            DataManager.getSchematicPlacementManager()
                    .markChunkForRebuild(chunk.x(), chunk.z());
            LitematicaRenderer.getInstance().getWorldRenderer()
                    .scheduleChunkRenders(chunk.x(), chunk.z(), false);
        }

        LitematicaRenderer.getInstance().getWorldRenderer().markNeedsUpdate();
    }

    private static Path key(Path repositoryDirectory)
    {
        return repositoryDirectory.toAbsolutePath().normalize();
    }

    private static BlockPos min(BlockPos first, BlockPos second)
    {
        return new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
    }

    private static BlockPos max(BlockPos first, BlockPos second)
    {
        return new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
    }

    public record WorldBounds(
            String regionName,
            BoundsStatus status,
            BlockPos min,
            BlockPos max)
    {
    }

    private record BoundDiff(
            Path repositoryDirectory,
            SchematicPlacement placement,
            LvcSubRegionStructuralDiff source,
            BlockPos origin,
            boolean enabled,
            List<WorldBounds> bounds,
            Map<BlockPos, BlockState> retiredBlocks,
            Map<ChunkPos, List<IntBoundingBox>> retiredBoxes,
            Set<ChunkPos> retiredChunks)
    {
        private static BoundDiff create(
                Path repositoryDirectory,
                SchematicPlacement placement,
                LvcSubRegionStructuralDiff source)
        {
            BlockPos origin = placement.getOrigin().immutable();
            List<WorldBounds> bounds = source.bounds().stream()
                    .map(value -> new WorldBounds(
                            value.regionName(),
                            value.status(),
                            worldPosition(origin, value.min()),
                            worldPosition(origin, value.max())))
                    .toList();
            Map<BlockPos, BlockState> retiredBlocks = new LinkedHashMap<>();

            source.retiredBlocks().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            Comparator.comparingInt(LvcIntPosition::x)
                                    .thenComparingInt(LvcIntPosition::y)
                                    .thenComparingInt(LvcIntPosition::z)))
                    .forEach(entry -> retiredBlocks.put(
                            worldPosition(origin, entry.getKey()), entry.getValue()));

            Map<ChunkPos, List<IntBoundingBox>> boxes = renderBoxes(retiredBlocks.keySet());
            return new BoundDiff(
                    repositoryDirectory,
                    placement,
                    source,
                    origin,
                    placement.isEnabled(),
                    bounds,
                    Map.copyOf(retiredBlocks),
                    boxes,
                    Set.copyOf(boxes.keySet())
            );
        }

        private boolean active()
        {
            return this.enabled;
        }

        private static Map<ChunkPos, List<IntBoundingBox>> renderBoxes(
                Set<BlockPos> positions)
        {
            Map<ChunkPos, List<BlockPos>> positionsByChunk = new HashMap<>();

            for (BlockPos position : positions)
            {
                positionsByChunk.computeIfAbsent(
                        new ChunkPos(position.getX() >> 4, position.getZ() >> 4),
                        ignored -> new ArrayList<>())
                        .add(position);
            }

            Map<ChunkPos, List<IntBoundingBox>> result = new HashMap<>();

            for (Map.Entry<ChunkPos, List<BlockPos>> entry : positionsByChunk.entrySet())
            {
                List<BlockPos> sorted = entry.getValue();
                sorted.sort(Comparator.comparingInt((BlockPos position) -> position.getY())
                        .thenComparingInt(BlockPos::getZ)
                        .thenComparingInt(BlockPos::getX));
                result.put(entry.getKey(), xRuns(sorted));
            }

            return Map.copyOf(result);
        }

        private static List<IntBoundingBox> xRuns(List<BlockPos> positions)
        {
            List<IntBoundingBox> result = new ArrayList<>();
            BlockPos start = null;
            BlockPos previous = null;

            for (BlockPos position : positions)
            {
                if (start == null || previous == null ||
                    position.getY() != previous.getY() ||
                    position.getZ() != previous.getZ() ||
                    position.getX() != previous.getX() + 1)
                {
                    appendRun(result, start, previous);
                    start = position;
                }

                previous = position;
            }

            appendRun(result, start, previous);
            return List.copyOf(result);
        }

        private static void appendRun(
                List<IntBoundingBox> result,
                @Nullable BlockPos start,
                @Nullable BlockPos end)
        {
            if (start != null && end != null)
            {
                result.add(new IntBoundingBox(
                        start.getX(), start.getY(), start.getZ(),
                        end.getX(), end.getY(), end.getZ()));
            }
        }

        private static BlockPos worldPosition(
                BlockPos origin,
                LvcIntPosition relative)
        {
            return origin.offset(relative.x(), relative.y(), relative.z()).immutable();
        }
    }
}
