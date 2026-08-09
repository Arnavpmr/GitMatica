package me.arnavpmr.lvc.diff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.minecraft.world.level.block.state.BlockState;

import me.arnavpmr.lvc.capture.LvcCapturePlanner;
import me.arnavpmr.lvc.capture.LvcRetiredCoveragePlan;
import me.arnavpmr.lvc.capture.LvcSiteWorkPlan;
import me.arnavpmr.lvc.model.LvcChunk;
import me.arnavpmr.lvc.model.LvcChunkCoordinate;
import me.arnavpmr.lvc.model.LvcIntPosition;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.semantic.LvcSemanticWorldApplier;
import me.arnavpmr.lvc.storage.LvcChunkCodec;

/**
 * Applied subregion-definition differences between committed HEAD and the
 * working manifest. Retired blocks are project-relative and always sourced
 * from HEAD; the current world is intentionally absent from this model.
 */
public final class LvcSubRegionStructuralDiff
{
    private static final LvcSubRegionStructuralDiff EMPTY =
            new LvcSubRegionStructuralDiff(List.of(), Map.of());

    private final List<Bounds> bounds;
    private final Map<LvcIntPosition, BlockState> retiredBlocks;

    private LvcSubRegionStructuralDiff(
            List<Bounds> bounds,
            Map<LvcIntPosition, BlockState> retiredBlocks)
    {
        this.bounds = List.copyOf(bounds);
        this.retiredBlocks = Map.copyOf(retiredBlocks);
    }

    public static LvcSubRegionStructuralDiff empty()
    {
        return EMPTY;
    }

    public static BuildSession begin(
            LvcManifest.Site headSite,
            LvcManifest.Site workingSite,
            ChunkObjectReader objectReader)
    {
        Objects.requireNonNull(headSite, "headSite");
        Objects.requireNonNull(workingSite, "workingSite");
        Objects.requireNonNull(objectReader, "objectReader");
        return new BuildSession(
                structuralBounds(headSite, workingSite),
                LvcRetiredCoveragePlan.between(headSite, workingSite),
                headSite,
                objectReader
        );
    }

    public List<Bounds> bounds()
    {
        return this.bounds;
    }

    public Map<LvcIntPosition, BlockState> retiredBlocks()
    {
        return this.retiredBlocks;
    }

    public boolean isEmpty()
    {
        return this.bounds.isEmpty() && this.retiredBlocks.isEmpty();
    }

    private static List<Bounds> structuralBounds(
            LvcManifest.Site headSite,
            LvcManifest.Site workingSite)
    {
        Map<String, LvcManifest.Region> head = regionsByName(headSite.regions());
        Map<String, LvcManifest.Region> working = regionsByName(workingSite.regions());
        java.util.SortedSet<String> names = new java.util.TreeSet<>();
        List<Bounds> result = new ArrayList<>();
        names.addAll(head.keySet());
        names.addAll(working.keySet());

        for (String name : names)
        {
            LvcManifest.Region previous = head.get(name);
            LvcManifest.Region current = working.get(name);

            if (previous == null)
            {
                result.add(bounds(current, BoundsStatus.ADDED));
            }
            else if (current == null)
            {
                result.add(bounds(previous, BoundsStatus.REMOVED));
            }
            else if (!sameGeometry(previous, current))
            {
                result.add(bounds(previous, BoundsStatus.CHANGED_HEAD));
                result.add(bounds(current, BoundsStatus.CHANGED_WORKING));
            }
        }

        return List.copyOf(result);
    }

    private static Map<String, LvcManifest.Region> regionsByName(
            List<LvcManifest.Region> regions)
    {
        Map<String, LvcManifest.Region> result = new TreeMap<>();

        for (LvcManifest.Region region : regions)
        {
            result.put(region.name(), region);
        }

        return result;
    }

    private static boolean sameGeometry(
            LvcManifest.Region first,
            LvcManifest.Region second)
    {
        return first.min().equals(second.min()) && first.size().equals(second.size());
    }

    private static Bounds bounds(LvcManifest.Region region, BoundsStatus status)
    {
        LvcIntPosition min = position(region.min());
        LvcIntPosition size = position(region.size());
        LvcIntPosition max = new LvcIntPosition(
                Math.addExact(min.x(), size.x() - 1),
                Math.addExact(min.y(), size.y() - 1),
                Math.addExact(min.z(), size.z() - 1)
        );
        return new Bounds(region.name(), status, min, max);
    }

    private static LvcIntPosition position(List<Integer> values)
    {
        return new LvcIntPosition(values.get(0), values.get(1), values.get(2));
    }

    public enum BoundsStatus
    {
        CHANGED_HEAD,
        CHANGED_WORKING,
        ADDED,
        REMOVED
    }

    public record Bounds(
            String regionName,
            BoundsStatus status,
            LvcIntPosition min,
            LvcIntPosition max)
    {
        public Bounds
        {
            Objects.requireNonNull(regionName, "regionName");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(min, "min");
            Objects.requireNonNull(max, "max");
        }
    }

    @FunctionalInterface
    public interface ChunkObjectReader
    {
        byte[] read(String objectId) throws IOException;
    }

    public static final class BuildSession
    {
        private final List<Bounds> bounds;
        private final List<LvcSiteWorkPlan.ChunkWork> retiredChunks;
        private final LvcManifest.Site headSite;
        private final ChunkObjectReader objectReader;
        private final Map<LvcIntPosition, BlockState> retiredBlocks = new LinkedHashMap<>();
        private int nextChunk;

        private BuildSession(
                List<Bounds> bounds,
                LvcRetiredCoveragePlan retiredCoverage,
                LvcManifest.Site headSite,
                ChunkObjectReader objectReader)
        {
            this.bounds = bounds;
            this.retiredChunks = retiredCoverage.chunks().stream()
                    .sorted((first, second) -> first.coordinate().compareTo(second.coordinate()))
                    .toList();
            this.headSite = headSite;
            this.objectReader = objectReader;
        }

        public boolean isComplete()
        {
            return this.nextChunk >= this.retiredChunks.size();
        }

        public boolean hasStructuralBounds()
        {
            return !this.bounds.isEmpty();
        }

        public int processedChunks()
        {
            return this.nextChunk;
        }

        public int totalChunks()
        {
            return this.retiredChunks.size();
        }

        public void processNextChunk() throws IOException
        {
            if (this.isComplete())
            {
                return;
            }

            LvcSiteWorkPlan.ChunkWork work = this.retiredChunks.get(this.nextChunk++);
            String objectId = this.headSite.fullHashes().get(work.coordinate().key());

            if (objectId == null)
            {
                throw new IOException(
                        "HEAD is missing content for retired LVC chunk " + work.coordinate().key());
            }

            LvcChunk chunk = LvcChunkCodec.decode(this.objectReader.read(objectId));
            this.collectRetiredBlocks(work, chunk);
        }

        public LvcSubRegionStructuralDiff result()
        {
            if (!this.isComplete())
            {
                throw new IllegalStateException("Structural diff build is incomplete");
            }

            if (this.bounds.isEmpty() && this.retiredBlocks.isEmpty())
            {
                return LvcSubRegionStructuralDiff.empty();
            }

            return new LvcSubRegionStructuralDiff(this.bounds, this.retiredBlocks);
        }

        private void collectRetiredBlocks(
                LvcSiteWorkPlan.ChunkWork work,
                LvcChunk chunk) throws IOException
        {
            BitSet tracked = chunk.trackedMask();
            BitSet retired = work.mask();
            int ordinal = 0;

            for (int index = tracked.nextSetBit(0);
                 index >= 0;
                 index = tracked.nextSetBit(index + 1))
            {
                if (retired.get(index))
                {
                    BlockState state = LvcSemanticWorldApplier.parseRestoreBlockState(
                            chunk.blockStateAtTrackedOrdinal(ordinal));

                    if (!state.isAir())
                    {
                        this.retiredBlocks.put(
                                projectPosition(work.coordinate(), index, chunk), state);
                    }
                }

                ordinal++;
            }
        }

        private static LvcIntPosition projectPosition(
                LvcChunkCoordinate coordinate,
                int index,
                LvcChunk chunk)
        {
            return LvcCapturePlanner.projectPosition(
                    coordinate,
                    index,
                    chunk.sizeX(),
                    chunk.sizeY(),
                    chunk.sizeZ()
            );
        }
    }
}
