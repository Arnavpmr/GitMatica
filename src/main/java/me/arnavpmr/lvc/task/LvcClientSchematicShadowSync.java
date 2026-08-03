package me.arnavpmr.lvc.task;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import me.arnavpmr.lvc.capture.LvcCapturePlanner;
import me.arnavpmr.lvc.capture.LvcRetiredCoveragePlan;
import me.arnavpmr.lvc.capture.LvcSiteWorkPlan;
import me.arnavpmr.lvc.model.LvcChunk;
import me.arnavpmr.lvc.model.LvcIntPosition;

final class LvcClientSchematicShadowSync
{
    private static final long SYNC_BUDGET_NANOS = 4_000_000L;
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final ClientLevel clientLevel;
    private final LitematicaSchematic schematic;
    private final BlockPos origin;
    private final List<String> regionNames;
    private final List<LvcSiteWorkPlan.ChunkWork> retiredCoverageChunks;
    private final LongOpenHashSet renderSections = new LongOpenHashSet();
    private final LongOpenHashSet renderChunks = new LongOpenHashSet();
    private int regionIndex;
    private int x;
    private int y;
    private int z;
    private int retiredChunkIndex;
    private int retiredMaskIndex = -1;
    private long processedVolume;
    private long totalVolume;
    private int pastedBlocks;
    private int changedBlocks;
    private int skippedStructureVoid;
    private int skippedUnloaded;

    LvcClientSchematicShadowSync(ClientLevel clientLevel, LitematicaSchematic schematic, BlockPos origin,
                                      LvcRetiredCoveragePlan retiredCoverage)
    {
        this.clientLevel = Objects.requireNonNull(clientLevel, "clientLevel");
        this.schematic = Objects.requireNonNull(schematic, "schematic");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.regionNames = List.copyOf(schematic.getAreas().keySet());
        this.retiredCoverageChunks = List.copyOf(
                Objects.requireNonNull(retiredCoverage, "retiredCoverage").chunks());

        for (String regionName : this.regionNames)
        {
            LitematicaBlockStateContainer container = Objects.requireNonNull(
                    schematic.getSubRegionContainer(regionName), "subRegionContainer");
            Vec3i size = container.getSize();
            this.totalVolume += (long) size.getX() * (long) size.getY() * (long) size.getZ();
        }

        this.totalVolume += retiredCoverage.blockCount();
    }

    boolean processNextBatch()
    {
        long deadline = Util.getNanos() + SYNC_BUDGET_NANOS;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        while (this.regionIndex < this.regionNames.size() && Util.getNanos() < deadline)
        {
            String regionName = this.regionNames.get(this.regionIndex);
            LitematicaBlockStateContainer container = Objects.requireNonNull(
                    this.schematic.getSubRegionContainer(regionName), "subRegionContainer");
            BlockPos regionPos = Objects.requireNonNull(this.schematic.getSubRegionPosition(regionName),
                    "subRegionPosition");
            Vec3i size = container.getSize();

            while (this.y < size.getY() && Util.getNanos() < deadline)
            {
                BlockState targetState = container.get(this.x, this.y, this.z);
                this.processedVolume++;

                if (targetState.is(Blocks.STRUCTURE_VOID))
                {
                    this.skippedStructureVoid++;
                }
                else
                {
                    this.applyTargetBlock(regionPos, targetState, mutable);
                }

                this.advance(size);
            }

            if (this.y >= size.getY())
            {
                this.regionIndex++;
                this.x = 0;
                this.y = 0;
                this.z = 0;
            }
        }

        while (this.regionIndex >= this.regionNames.size() &&
                this.retiredChunkIndex < this.retiredCoverageChunks.size() &&
                Util.getNanos() < deadline)
        {
            LvcSiteWorkPlan.ChunkWork work = this.retiredCoverageChunks.get(this.retiredChunkIndex);
            BitSet mask = work.mask();

            if (this.retiredMaskIndex < 0)
            {
                this.retiredMaskIndex = mask.nextSetBit(0);
            }

            if (this.retiredMaskIndex < 0)
            {
                this.retiredChunkIndex++;
                continue;
            }

            LvcIntPosition projectPos = LvcCapturePlanner.projectPosition(
                    work.coordinate(), this.retiredMaskIndex,
                    LvcChunk.DEFAULT_SIZE, LvcChunk.DEFAULT_SIZE, LvcChunk.DEFAULT_SIZE);
            this.processedVolume++;
            this.applyProjectTarget(projectPos, Blocks.AIR.defaultBlockState(), mutable);
            this.retiredMaskIndex = mask.nextSetBit(this.retiredMaskIndex + 1);

            if (this.retiredMaskIndex < 0)
            {
                this.retiredChunkIndex++;
            }
        }

        return this.regionIndex >= this.regionNames.size() &&
                this.retiredChunkIndex >= this.retiredCoverageChunks.size();
    }

    private void applyTargetBlock(BlockPos regionPos, BlockState targetState, BlockPos.MutableBlockPos mutable)
    {
        this.applyWorldTarget(
                this.origin.getX() + regionPos.getX() + this.x,
                this.origin.getY() + regionPos.getY() + this.y,
                this.origin.getZ() + regionPos.getZ() + this.z,
                targetState,
                mutable);
    }

    private void applyProjectTarget(LvcIntPosition projectPos, BlockState targetState,
                                    BlockPos.MutableBlockPos mutable)
    {
        this.applyWorldTarget(
                this.origin.getX() + projectPos.x(),
                this.origin.getY() + projectPos.y(),
                this.origin.getZ() + projectPos.z(),
                targetState,
                mutable);
    }

    private void applyWorldTarget(int worldX, int worldY, int worldZ, BlockState targetState,
                                  BlockPos.MutableBlockPos mutable)
    {
        int sectionX = SectionPos.blockToSectionCoord(worldX);
        int sectionY = SectionPos.blockToSectionCoord(worldY);
        int sectionZ = SectionPos.blockToSectionCoord(worldZ);

        if (!this.clientLevel.hasChunk(sectionX, sectionZ))
        {
            this.skippedUnloaded++;
            return;
        }

        mutable.set(worldX, worldY, worldZ);
        this.pastedBlocks++;
        this.renderSections.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
        this.renderChunks.add(chunkKey(sectionX, sectionZ));

        BlockState currentState = this.clientLevel.getBlockState(mutable);

        if (!statesEquivalent(currentState, targetState))
        {
            this.clientLevel.setBlock(mutable, targetState, SET_FLAGS);
            this.changedBlocks++;
        }
    }

    private void advance(Vec3i size)
    {
        this.x++;

        if (this.x >= size.getX())
        {
            this.x = 0;
            this.z++;
        }

        if (this.z >= size.getZ())
        {
            this.z = 0;
            this.y++;
        }
    }

    void refreshRenderState()
    {
        LongIterator sectionIterator = this.renderSections.iterator();

        while (sectionIterator.hasNext())
        {
            long section = sectionIterator.nextLong();
            this.clientLevel.setSectionDirtyWithNeighbors(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }

        LongIterator chunkIterator = this.renderChunks.iterator();

        while (chunkIterator.hasNext())
        {
            long chunk = chunkIterator.nextLong();
            SchematicWorldRefresher.INSTANCE.markSchematicChunksForRenderUpdate(chunkKeyX(chunk), chunkKeyZ(chunk));
        }
    }

    private static long chunkKey(int x, int z)
    {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    private static int chunkKeyX(long key)
    {
        return (int) (key & 0xFFFFFFFFL);
    }

    private static int chunkKeyZ(long key)
    {
        return (int) (key >>> 32);
    }

    private static boolean statesEquivalent(BlockState currentState, BlockState targetState)
    {
        return currentState.equals(targetState) || (currentState.isAir() && targetState.isAir());
    }

    int regionCount()
    {
        return this.regionNames.size();
    }

    long processedVolume()
    {
        return this.processedVolume;
    }

    long totalVolume()
    {
        return this.totalVolume;
    }

    int pastedBlocks()
    {
        return this.pastedBlocks;
    }

    int changedBlocks()
    {
        return this.changedBlocks;
    }

    int skippedStructureVoid()
    {
        return this.skippedStructureVoid;
    }

    int skippedUnloaded()
    {
        return this.skippedUnloaded;
    }

    int renderSectionCount()
    {
        return this.renderSections.size();
    }

    int renderChunkCount()
    {
        return this.renderChunks.size();
    }
}
