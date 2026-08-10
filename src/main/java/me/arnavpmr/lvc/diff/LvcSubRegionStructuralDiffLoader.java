package me.arnavpmr.lvc.diff;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import me.arnavpmr.lvc.git.LvcGitTreeReader;
import me.arnavpmr.lvc.model.LvcManifest;
import me.arnavpmr.lvc.storage.LvcChunkStore;
import me.arnavpmr.lvc.storage.LvcRepository;
import me.arnavpmr.lvc.storage.LvcSemanticRepository;

/** Opens a bounded, chunk-at-a-time structural diff build against HEAD. */
public final class LvcSubRegionStructuralDiffLoader implements AutoCloseable
{
    private final Git git;
    private final RevWalk walk;
    private final RevCommit head;
    private final String workingDefinitionId;
    private final LvcSubRegionStructuralDiff.BuildSession session;

    private LvcSubRegionStructuralDiffLoader(
            Git git,
            RevWalk walk,
            RevCommit head,
            String workingDefinitionId,
            LvcSubRegionStructuralDiff.BuildSession session)
    {
        this.git = git;
        this.walk = walk;
        this.head = head;
        this.workingDefinitionId = workingDefinitionId;
        this.session = session;
    }

    public static LvcSubRegionStructuralDiffLoader open(Path repositoryDirectory)
            throws IOException
    {
        Objects.requireNonNull(repositoryDirectory, "repositoryDirectory");
        Git git = Git.open(repositoryDirectory.toFile());
        RevWalk walk = new RevWalk(git.getRepository());

        try
        {
            Repository repository = git.getRepository();
            ObjectId headId = LvcRepository.resolveHead(repositoryDirectory);

            if (headId == null)
            {
                throw new IOException("Gitmatica project has no HEAD commit");
            }

            RevCommit head = LvcGitTreeReader.resolveCommit(
                    repository, walk, headId.getName());
            LvcManifest committed =
                    LvcSemanticRepository.readCommitManifest(repository, head);
            LvcManifest working =
                    LvcSemanticRepository.readManifest(repositoryDirectory);
            String siteId = LvcSemanticRepository.defaultSiteId(working);
            LvcSubRegionStructuralDiff.BuildSession session =
                    LvcSubRegionStructuralDiff.begin(
                            committed.site(siteId),
                            working.site(siteId),
                            objectId -> readCommittedObject(repository, head, objectId)
                    );
            return new LvcSubRegionStructuralDiffLoader(
                    git,
                    walk,
                    head,
                    LvcSemanticRepository.trackingOverlayDefinitionId(working),
                    session);
        }
        catch (Exception e)
        {
            walk.close();
            git.close();

            if (e instanceof IOException ioException)
            {
                throw ioException;
            }

            throw new IOException("Failed to prepare subregion structural diff", e);
        }
    }

    public static boolean hasStructuralBounds(Path repositoryDirectory)
            throws IOException
    {
        try (LvcSubRegionStructuralDiffLoader loader = open(repositoryDirectory))
        {
            return loader.session.hasStructuralBounds();
        }
    }

    public boolean isComplete()
    {
        return this.session.isComplete();
    }

    public void processNextChunk() throws IOException
    {
        this.session.processNextChunk();
    }

    public int processedChunks()
    {
        return this.session.processedChunks();
    }

    public int totalChunks()
    {
        return this.session.totalChunks();
    }

    public LvcSubRegionStructuralDiff result()
    {
        return this.session.result();
    }

    public String headCommitId()
    {
        return this.head.getName();
    }

    public String workingDefinitionId()
    {
        return this.workingDefinitionId;
    }

    @Override
    public void close()
    {
        this.walk.close();
        this.git.close();
    }

    private static byte[] readCommittedObject(
            Repository repository,
            RevCommit head,
            String objectId) throws IOException
    {
        byte[] bytes = LvcGitTreeReader.readCommitFile(
                repository,
                head,
                LvcChunkStore.objectRepositoryPath(objectId)
        );

        if (bytes == null)
        {
            throw new IOException(
                    "HEAD is missing LVC object " + objectId + " at " + head.getName());
        }

        return bytes;
    }
}
