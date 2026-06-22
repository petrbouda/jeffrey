/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.performance.analyst.recommendations;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.persistence.Platform;
import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Clones a project's configured repository into a throwaway temp directory so the AI can read it. The
 * clone is shallow (single commit of the default branch, no tags) — recommendations only need the
 * current source, not the history. The personal access token, when present, authenticates over HTTPS;
 * it is never logged.
 */
public class RepositoryCloner {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryCloner.class);

    private static final int SHALLOW_DEPTH = 1;

    // GitHub and GitLab both accept a PAT as the HTTPS password; the username is a platform-specific
    // placeholder that the platform ignores in favour of the token.
    private static final String GITHUB_TOKEN_USERNAME = "x-access-token";
    private static final String GITLAB_TOKEN_USERNAME = "oauth2";

    private final TempDirFactory tempDirFactory;

    public RepositoryCloner(TempDirFactory tempDirFactory) {
        this.tempDirFactory = tempDirFactory;
    }

    /**
     * Shallow-clones {@code url} (authenticating with {@code token} when non-blank) and returns a
     * handle whose {@link ClonedRepository#close()} deletes the checkout.
     */
    public ClonedRepository clone(String url, String token, Platform platform) {
        TempDirectory tempDirectory = tempDirFactory.newTempDir(IDGenerator.generate());
        Path root = tempDirectory.path();
        try {
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(root.toFile())
                    .setDepth(SHALLOW_DEPTH)
                    .setNoTags();

            if (token != null && !token.isBlank()) {
                cloneCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(tokenUsername(platform), token));
            }

            Duration elapsed = Measuring.r(() -> {
                try (Git git = cloneCommand.call()) {
                    // try-with-resources closes the Git handle; the working tree stays on disk.
                } catch (GitAPIException e) {
                    // Re-thrown unchecked so it surfaces through the outer catch; JGit redacts credentials.
                    throw new IllegalStateException(e.getMessage(), e);
                }
            });

            LOG.info("Cloned repository for recommendations: url={} platform={} duration_in_sec={}",
                    url, platform.code(), elapsed.toSeconds());
            return new ClonedRepository(root, tempDirectory);
        } catch (Exception e) {
            tempDirectory.close();
            // The message may carry the URL but never the token (JGit redacts credentials).
            throw Exceptions.invalidRequest("Failed to clone repository: " + e.getMessage());
        }
    }

    private static String tokenUsername(Platform platform) {
        return switch (platform) {
            case GITHUB -> GITHUB_TOKEN_USERNAME;
            case GITLAB -> GITLAB_TOKEN_USERNAME;
        };
    }
}
