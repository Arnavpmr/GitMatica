package me.arnavpmr.lvc.overlay;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import me.arnavpmr.lvc.git.LvcGitBranchOps;
import me.arnavpmr.lvc.git.LvcGitTreeReader;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.storage.LvcRepository;
import me.arnavpmr.lvc.storage.LvcSemanticRepository;

public final class LvcTrackingOverlayManifestResolver
{
    private LvcTrackingOverlayManifestResolver()
    {
    }

    public static Source resolve(Path repositoryDirectory) throws IOException
    {
        LvcManifest working = LvcSemanticRepository.readManifest(repositoryDirectory);
        ObjectId head = LvcRepository.resolveHead(repositoryDirectory);

        if (head == null || !hasUncommittedChanges(repositoryDirectory))
        {
            return new Source(working, null, false, false);
        }

        LvcManifest committed = readCommittedManifest(repositoryDirectory, head.getName());

        if (LvcSemanticRepository.sameRegionDefinitions(working, committed))
        {
            return new Source(committed, head.getName(), true, false);
        }

        return new Source(withCommittedContent(working, committed), head.getName(), true, true);
    }

    private static LvcManifest withCommittedContent(LvcManifest working, LvcManifest committed)
    {
        Map<String, LvcManifest.Site> committedSites = new HashMap<>();

        for (LvcManifest.Site site : committed.sites())
        {
            committedSites.put(site.id(), site);
        }

        List<LvcManifest.Site> sites = working.sites().stream().map(site ->
        {
            LvcManifest.Site committedSite = committedSites.get(site.id());
            return committedSite == null ? site.withHashRefs(Map.of(), Map.of()) :
                    site.withHashRefs(
                            committedSite.fullHashes(), committedSite.trackedHashesForComparison());
        }).toList();
        return new LvcManifest(working.format(), working.name(), working.content(), sites).validate();
    }

    private static LvcManifest readCommittedManifest(Path repositoryDirectory, String commitId)
            throws IOException
    {
        try (Git git = Git.open(repositoryDirectory.toFile());
             RevWalk walk = new RevWalk(git.getRepository()))
        {
            Repository repository = git.getRepository();
            RevCommit commit = LvcGitTreeReader.resolveCommit(repository, walk, commitId);
            return LvcSemanticRepository.readCommitManifest(repository, commit);
        }
    }

    private static boolean hasUncommittedChanges(Path repositoryDirectory) throws IOException
    {
        try
        {
            return LvcGitBranchOps.hasUncommittedChanges(repositoryDirectory);
        }
        catch (org.eclipse.jgit.api.errors.GitAPIException e)
        {
            throw new IOException("Failed to inspect Gitmatica working tree", e);
        }
    }

    public record Source(LvcManifest manifest, @Nullable String commitId,
                         boolean committedHead, boolean workingDefinitions)
    {
    }
}
