package me.arnavpmr.lvc.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.storage.LvcManifestJsonCodec;
import me.arnavpmr.lvc.storage.LvcSemanticRepository;

final class LvcSemanticMergeEngine
{
    private LvcSemanticMergeEngine()
    {
    }

    static LvcSemanticMergeResult merge(Path repositoryDirectory, Repository repository,
                                        RevCommit baseCommit, RevCommit currentCommit,
                                        RevCommit sourceCommit,
                                        @Nullable LvcBranchMergeConflictResolution resolution)
            throws IOException
    {
        LvcManifest baseManifest = LvcSemanticRepository.readCommitManifest(repository, baseCommit);
        LvcManifest currentManifest = LvcSemanticRepository.readCommitManifest(repository, currentCommit);
        LvcManifest sourceManifest = LvcSemanticRepository.readCommitManifest(repository, sourceCommit);
        LvcManifest metadata = chooseManifestMetadata(
                baseManifest, currentManifest, sourceManifest, resolution);
        Map<String, LvcManifest.Site> baseSites = sitesById(baseManifest);
        Map<String, LvcManifest.Site> currentSites = sitesById(currentManifest);
        Map<String, LvcManifest.Site> sourceSites = sitesById(sourceManifest);
        List<LvcManifest.Site> mergedSites = new ArrayList<>(metadata.sites().size());
        int mergedChunks = 0;

        for (LvcManifest.Site metadataSite : metadata.sites())
        {
            LvcMergeSiteResult siteMerge = mergeSite(
                    repositoryDirectory,
                    repository,
                    baseCommit,
                    currentCommit,
                    sourceCommit,
                    metadataSite,
                    baseSites.get(metadataSite.id()),
                    currentSites.get(metadataSite.id()),
                    sourceSites.get(metadataSite.id()),
                    resolution
            );
            LvcManifest.Site mergedSite = metadataSite
                    .withManualOrigin(siteMerge.manualOrigin())
                    .withRegions(siteMerge.regions())
                    .withHashRefs(siteMerge.fullHashes(), siteMerge.trackedHashes());
            mergedSites.add(mergedSite);
            mergedChunks += siteMerge.mergedChunks();
        }

        return new LvcSemanticMergeResult(
                new LvcManifest(
                        mergedFormat(baseManifest, currentManifest, sourceManifest),
                        metadata.name(),
                        metadata.content(),
                        mergedSites),
                mergedChunks
        );
    }

    private static LvcManifest chooseManifestMetadata(
            LvcManifest baseManifest,
            LvcManifest currentManifest,
            LvcManifest sourceManifest,
            @Nullable LvcBranchMergeConflictResolution resolution)
            throws LvcMergeConflictException
    {
        String baseJson = metadataJson(baseManifest);
        String currentJson = metadataJson(currentManifest);
        String sourceJson = metadataJson(sourceManifest);

        if (currentJson.equals(sourceJson))
        {
            return currentManifest;
        }

        if (currentJson.equals(baseJson))
        {
            return sourceManifest;
        }

        if (sourceJson.equals(baseJson))
        {
            return currentManifest;
        }

        if (resolution == null)
        {
            throw new LvcMergeConflictException(
                    LvcMergeConflictException.Reason.MANIFEST_METADATA,
                    "LVC manifest metadata changed on both branches"
            );
        }

        return switch (resolution)
        {
            case BASE -> baseManifest;
            case INCOMING -> sourceManifest;
            case YOURS -> currentManifest;
        };
    }

    private static String metadataJson(LvcManifest manifest)
    {
        List<LvcManifest.Site> sites = manifest.sites().stream()
                .map(site -> site
                        .withRegions(List.of())
                        .withManualOrigin(LvcManifest.ZERO_ORIGIN))
                .toList();
        return LvcManifestJsonCodec.encode(new LvcManifest(
                LvcManifest.FORMAT_V1,
                manifest.name(),
                manifest.content(),
                sites));
    }

    private static String mergedFormat(LvcManifest... manifests)
    {
        for (LvcManifest manifest : manifests)
        {
            if (LvcManifest.FORMAT_V2.equals(manifest.format()))
            {
                return LvcManifest.FORMAT_V2;
            }
        }

        return LvcManifest.FORMAT_V1;
    }

    private static LvcMergeSiteResult mergeSite(
            Path repositoryDirectory,
            Repository repository,
            RevCommit baseCommit,
            RevCommit currentCommit,
            RevCommit sourceCommit,
            LvcManifest.Site metadataSite,
            @Nullable LvcManifest.Site baseSite,
            @Nullable LvcManifest.Site currentSite,
            @Nullable LvcManifest.Site sourceSite,
            @Nullable LvcBranchMergeConflictResolution resolution)
            throws IOException
    {
        if (baseSite == null)
        {
            if (currentSite == null && sourceSite == null)
            {
                return emptySite();
            }

            if (currentSite == null)
            {
                return retainedSite(
                        repositoryDirectory, repository, sourceCommit, sourceSite);
            }

            if (sourceSite == null || sameSite(currentSite, sourceSite))
            {
                return retainedSite(
                        repositoryDirectory, repository, currentCommit, currentSite);
            }

            return resolveWholeSite(
                    repositoryDirectory,
                    repository,
                    baseCommit,
                    currentCommit,
                    sourceCommit,
                    null,
                    currentSite,
                    sourceSite,
                    resolution,
                    LvcMergeConflictException.Reason.SITE_ADD,
                    "LVC site was added differently on both branches: " + currentSite.id()
            );
        }

        if (currentSite == null || sourceSite == null)
        {
            return resolveWholeSite(
                    repositoryDirectory,
                    repository,
                    baseCommit,
                    currentCommit,
                    sourceCommit,
                    baseSite,
                    currentSite,
                    sourceSite,
                    resolution,
                    LvcMergeConflictException.Reason.SITE_DELETE,
                    "LVC site was deleted on one branch and changed on another: " + baseSite.id()
            );
        }

        LvcRegionMergeResult regionMerge = LvcRegionMergeEngine.merge(
                repositoryDirectory,
                repository,
                baseCommit,
                currentCommit,
                sourceCommit,
                metadataSite.withManualOrigin(mergeManualOrigin(
                        baseSite, currentSite, sourceSite, resolution)),
                baseSite,
                currentSite,
                sourceSite,
                resolution
        );
        LvcManifest.Site site = regionMerge.site();
        return new LvcMergeSiteResult(
                site.fullHashes(),
                site.trackedHashesForComparison(),
                regionMerge.mergedChunks(),
                site.regions(),
                site.manualOrigin()
        );
    }

    private static List<Integer> mergeManualOrigin(
            LvcManifest.Site baseSite,
            LvcManifest.Site currentSite,
            LvcManifest.Site sourceSite,
            @Nullable LvcBranchMergeConflictResolution resolution)
            throws LvcMergeConflictException
    {
        List<Integer> base = baseSite.manualOrigin();
        List<Integer> current = currentSite.manualOrigin();
        List<Integer> source = sourceSite.manualOrigin();

        if (current.equals(source))
        {
            return current;
        }

        if (current.equals(base))
        {
            return source;
        }

        if (source.equals(base))
        {
            return current;
        }

        if (resolution == null)
        {
            throw new LvcMergeConflictException(
                    LvcMergeConflictException.Reason.MANUAL_ORIGIN,
                    "LVC manual origin changed differently on both branches: " +
                            baseSite.id());
        }

        return switch (resolution)
        {
            case BASE -> base;
            case INCOMING -> source;
            case YOURS -> current;
        };
    }

    private static LvcMergeSiteResult resolveWholeSite(
            Path repositoryDirectory,
            Repository repository,
            RevCommit baseCommit,
            RevCommit currentCommit,
            RevCommit sourceCommit,
            @Nullable LvcManifest.Site baseSite,
            @Nullable LvcManifest.Site currentSite,
            @Nullable LvcManifest.Site sourceSite,
            @Nullable LvcBranchMergeConflictResolution resolution,
            LvcMergeConflictException.Reason reason,
            String conflictMessage) throws IOException
    {
        if (resolution == null)
        {
            throw new LvcMergeConflictException(reason, conflictMessage);
        }

        LvcManifest.Site selectedSite = switch (resolution)
        {
            case BASE -> baseSite;
            case INCOMING -> sourceSite;
            case YOURS -> currentSite;
        };
        RevCommit selectedCommit = switch (resolution)
        {
            case BASE -> baseCommit;
            case INCOMING -> sourceCommit;
            case YOURS -> currentCommit;
        };

        if (selectedSite == null)
        {
            return emptySite();
        }

        return retainedSite(
                repositoryDirectory, repository, selectedCommit, selectedSite);
    }

    private static LvcMergeSiteResult retainedSite(
            Path repositoryDirectory,
            Repository repository,
            RevCommit commit,
            LvcManifest.Site site) throws IOException
    {
        LvcMergeObjectResolver.ensureSiteObjects(
                repositoryDirectory, repository, commit, site);
        return new LvcMergeSiteResult(
                site.fullHashes(),
                site.trackedHashesForComparison(),
                0,
                site.regions(),
                site.manualOrigin()
        );
    }

    private static LvcMergeSiteResult emptySite()
    {
        return new LvcMergeSiteResult(
                Map.of(), Map.of(), 0, List.of(), LvcManifest.ZERO_ORIGIN);
    }

    private static boolean sameSite(LvcManifest.Site currentSite, LvcManifest.Site sourceSite)
    {
        return Objects.equals(currentSite.manualOrigin(), sourceSite.manualOrigin()) &&
                Objects.equals(currentSite.regions(), sourceSite.regions()) &&
                Objects.equals(currentSite.fullHashes(), sourceSite.fullHashes()) &&
                Objects.equals(
                        currentSite.trackedHashesForComparison(),
                        sourceSite.trackedHashesForComparison()
                );
    }

    private static Map<String, LvcManifest.Site> sitesById(LvcManifest manifest)
    {
        Map<String, LvcManifest.Site> sites = new HashMap<>();

        for (LvcManifest.Site site : manifest.sites())
        {
            sites.put(site.id(), site);
        }

        return sites;
    }
}

record LvcSemanticMergeResult(LvcManifest manifest, int mergedChunks)
{
}

record LvcMergeSiteResult(Map<String, String> fullHashes,
                          Map<String, String> trackedHashes,
                          int mergedChunks,
                          List<LvcManifest.Region> regions,
                          List<Integer> manualOrigin)
{
}

record LvcMergeChunkRef(String fullHash, String trackedHash)
{
}

record LvcMergeBlockPayload(String blockState, @Nullable byte[] blockEntityNbt)
{
    @Override
    public byte[] blockEntityNbt()
    {
        return this.blockEntityNbt == null ? null : this.blockEntityNbt.clone();
    }
}
