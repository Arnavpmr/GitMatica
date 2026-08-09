package me.arnavpmr.lvc.integration.litematica.verifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;

/** Stable HEAD-sourced removals that intentionally ignore live world changes. */
final class VerifierStructuralMismatches
{
    private final Map<BlockPos, BlockMismatch> active = new LinkedHashMap<>();
    private final Map<BlockPos, BlockMismatch> hidden = new LinkedHashMap<>();
    private final Set<Key> ignored = new HashSet<>();

    void clearForVerifierReset()
    {
        this.active.clear();
        this.hidden.clear();
    }

    void setExpectedStates(Map<BlockPos, BlockState> expectedStates)
    {
        this.active.clear();
        this.hidden.clear();

        if (expectedStates.isEmpty())
        {
            this.ignored.clear();
            return;
        }

        BlockState air = Blocks.AIR.defaultBlockState();

        for (Map.Entry<BlockPos, BlockState> entry : expectedStates.entrySet())
        {
            BlockMismatch mismatch = new BlockMismatch(
                    MismatchType.MISSING, entry.getValue(), air, 1);
            Map<BlockPos, BlockMismatch> destination =
                    this.ignored.contains(Key.of(mismatch)) ? this.hidden : this.active;
            destination.put(entry.getKey().immutable(), mismatch);
        }
    }

    Map<BlockPos, BlockMismatch> active()
    {
        return Map.copyOf(this.active);
    }

    int activeCount()
    {
        return this.active.size();
    }

    boolean hide(BlockMismatch mismatch)
    {
        Key key = Key.of(mismatch);
        boolean found = false;

        for (Map.Entry<BlockPos, BlockMismatch> entry :
                new LinkedHashMap<>(this.active).entrySet())
        {
            if (key.equals(Key.of(entry.getValue())))
            {
                this.active.remove(entry.getKey());
                this.hidden.put(entry.getKey(), entry.getValue());
                found = true;
            }
        }

        if (found)
        {
            this.ignored.add(key);
        }

        return found;
    }

    void resetHidden()
    {
        this.ignored.clear();
        this.active.putAll(this.hidden);
        this.hidden.clear();
    }

    boolean hasHidden()
    {
        return !this.hidden.isEmpty() || !this.ignored.isEmpty();
    }

    private record Key(
            MismatchType type,
            BlockState expected,
            BlockState found)
    {
        private static Key of(BlockMismatch mismatch)
        {
            return new Key(
                    mismatch.mismatchType(),
                    mismatch.stateExpected(),
                    mismatch.stateFound());
        }
    }
}
