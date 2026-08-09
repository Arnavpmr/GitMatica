package me.arnavpmr.lvc;

import static me.arnavpmr.lvc.LvcIntegrationFixtures.bootstrapMinecraft;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.level.block.Blocks;

import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff.Bounds;
import me.arnavpmr.lvc.diff.LvcSubRegionStructuralDiff.BoundsStatus;
import me.arnavpmr.lvc.model.LvcChunk;
import me.arnavpmr.lvc.model.LvcIntPosition;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.storage.LvcChunkCodec;

final class LvcSubRegionStructuralDiffIntegrationTest
{
    private static final String OBJECT_ID = "head-object";

    private LvcSubRegionStructuralDiffIntegrationTest()
    {
    }

    static void runAll() throws Exception
    {
        IntegrationTestSupport.run(
                "subregion structural diff freezes non-air retired HEAD blocks",
                LvcSubRegionStructuralDiffIntegrationTest::retiredBlocksComeFromHead);
        IntegrationTestSupport.run(
                "subregion structural diff subtracts overlapping working bounds",
                LvcSubRegionStructuralDiffIntegrationTest::workingUnionProtectsOverlap);
        IntegrationTestSupport.run(
                "subregion rename is removed plus added without block churn",
                LvcSubRegionStructuralDiffIntegrationTest::renameKeepsCoverage);
    }

    private static void retiredBlocksComeFromHead() throws Exception
    {
        bootstrapMinecraft();
        LvcManifest.Site head = site(
                List.of(region("Hall", 0, 3)), Map.of("0,0,0", OBJECT_ID));
        LvcManifest.Site working = site(
                List.of(region("Hall", 0, 1)), Map.of("0,0,0", OBJECT_ID));
        LvcSubRegionStructuralDiff diff = build(
                head,
                working,
                chunk(List.of(
                        "minecraft:stone",
                        "minecraft:dirt",
                        "minecraft:air")));

        IntegrationTestSupport.assertEquals(
                Map.of(new LvcIntPosition(1, 0, 0), Blocks.DIRT.defaultBlockState()),
                diff.retiredBlocks(),
                "retired coverage should keep non-air HEAD content and omit HEAD air");
        IntegrationTestSupport.assertEquals(
                List.of(
                        new Bounds(
                                "Hall", BoundsStatus.CHANGED_HEAD,
                                new LvcIntPosition(0, 0, 0),
                                new LvcIntPosition(2, 0, 0)),
                        new Bounds(
                                "Hall", BoundsStatus.CHANGED_WORKING,
                                new LvcIntPosition(0, 0, 0),
                                new LvcIntPosition(0, 0, 0))),
                diff.bounds(),
                "changed region should retain both HEAD and working bounds");
    }

    private static void workingUnionProtectsOverlap() throws Exception
    {
        bootstrapMinecraft();
        LvcManifest.Site head = site(
                List.of(region("A", 0, 3), region("B", 2, 2)),
                Map.of("0,0,0", OBJECT_ID));
        LvcManifest.Site working = site(
                List.of(region("A", 0, 1), region("B", 2, 2)),
                Map.of("0,0,0", OBJECT_ID));
        LvcSubRegionStructuralDiff diff = build(
                head,
                working,
                chunk(List.of(
                        "minecraft:stone",
                        "minecraft:dirt",
                        "minecraft:gold_block",
                        "minecraft:diamond_block")));

        IntegrationTestSupport.assertEquals(
                Set.of(new LvcIntPosition(1, 0, 0)),
                diff.retiredBlocks().keySet(),
                "coverage retained by another working region must not be removed");
    }

    private static void renameKeepsCoverage() throws Exception
    {
        bootstrapMinecraft();
        LvcManifest.Site head = site(
                List.of(region("Before", 0, 2)), Map.of("0,0,0", OBJECT_ID));
        LvcManifest.Site working = site(
                List.of(region("After", 0, 2)), Map.of("0,0,0", OBJECT_ID));
        LvcSubRegionStructuralDiff diff = build(
                head,
                working,
                chunk(List.of("minecraft:stone", "minecraft:dirt")));

        IntegrationTestSupport.assertTrue(
                diff.retiredBlocks().isEmpty(),
                "identical delete/add coverage should not create block diffs");
        IntegrationTestSupport.assertEquals(
                Set.of("After:ADDED", "Before:REMOVED"),
                diff.bounds().stream()
                        .map(value -> value.regionName() + ":" + value.status())
                        .collect(Collectors.toSet()),
                "rename should preserve file-like removed and added identities");
    }

    private static LvcSubRegionStructuralDiff build(
            LvcManifest.Site head,
            LvcManifest.Site working,
            LvcChunk chunk) throws Exception
    {
        byte[] bytes = LvcChunkCodec.encode(chunk);
        LvcSubRegionStructuralDiff.BuildSession session =
                LvcSubRegionStructuralDiff.begin(
                        head,
                        working,
                        objectId ->
                        {
                            IntegrationTestSupport.assertEquals(
                                    OBJECT_ID, objectId, "HEAD object id");
                            return bytes;
                        });

        while (!session.isComplete())
        {
            session.processNextChunk();
        }

        return session.result();
    }

    private static LvcChunk chunk(List<String> states)
    {
        BitSet mask = new BitSet(LvcChunk.DEFAULT_VOLUME);
        mask.set(0, states.size());
        return LvcChunk.fromTrackedBlockStates(mask, states);
    }

    private static LvcManifest.Site site(
            List<LvcManifest.Region> regions,
            Map<String, String> fullHashes)
    {
        return new LvcManifest.Site(
                "main", "Main", "minecraft:overworld", regions, fullHashes);
    }

    private static LvcManifest.Region region(String name, int x, int sizeX)
    {
        return new LvcManifest.Region(
                name, List.of(x, 0, 0), List.of(sizeX, 1, 1));
    }
}
