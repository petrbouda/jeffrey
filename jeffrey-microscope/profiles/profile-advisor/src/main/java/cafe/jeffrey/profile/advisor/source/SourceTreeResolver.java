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

package cafe.jeffrey.profile.advisor.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Turns a configured folder path into a {@link SourceTree} the model may read, or refuses with a
 * message that says what to fix.
 *
 * <p>Microscope runs on the machine that holds the source, so there is nothing to clone: the user
 * points at their working copy and the tools read it in place. That removes a network dependency, a
 * credential store and a whole class of "is this the right revision?" ambiguity — and adds one
 * obligation, which is that this class is the only thing deciding which directory the model gets to
 * see. Validation is therefore strict and happens once, up front: the path is canonicalized here so
 * every later containment check compares symlink-free paths.</p>
 */
public class SourceTreeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(SourceTreeResolver.class);

    private static final String NOT_CONFIGURED =
            "No source folder configured for this project. Set it under Advisor → Source & Settings.";
    private static final String NOT_ABSOLUTE =
            "The source folder must be an absolute path: ";
    private static final String NOT_A_DIRECTORY =
            "The configured source folder is not a directory: ";
    private static final String MISSING =
            "The configured source folder does not exist: ";
    private static final String UNREADABLE =
            "The configured source folder is not readable: ";

    private final RecordingCommitResolver recordingCommitResolver;

    public SourceTreeResolver(RecordingCommitResolver recordingCommitResolver) {
        this.recordingCommitResolver = recordingCommitResolver;
    }

    /**
     * Validates {@code configuredPath} and pairs it with the commit information needed to tell the user
     * whether the folder matches the profile.
     *
     * @param configuredPath the absolute path the project's advisor settings hold
     * @param recordingId    the recording the profile came from, used to look up its build commit
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when the path is unusable
     */
    public SourceTree resolve(String configuredPath, String recordingId) {
        Path root = validate(configuredPath);
        String resolvedRef = LocalGitProbe.headCommit(root).orElse(null);
        String recordingRef = recordingCommitResolver.resolve(recordingId).orElse(null);

        SourceTree tree = new SourceTree(root, resolvedRef, recordingRef);
        if (tree.refMismatch()) {
            LOG.info("Source folder is on a different commit than the recording: root={} folder_ref={} recording_ref={}",
                    root, resolvedRef, recordingRef);
        }
        return tree;
    }

    private static Path validate(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw Exceptions.invalidRequest(NOT_CONFIGURED);
        }

        Path candidate;
        try {
            candidate = Path.of(configuredPath.strip());
        } catch (InvalidPathException e) {
            throw Exceptions.invalidRequest(NOT_ABSOLUTE + configuredPath);
        }

        // A relative path would resolve against the server's working directory, which is not a place the
        // user was thinking about when they typed it. Refuse rather than guess.
        if (!candidate.isAbsolute()) {
            throw Exceptions.invalidRequest(NOT_ABSOLUTE + configuredPath);
        }
        if (!Files.exists(candidate)) {
            throw Exceptions.invalidRequest(MISSING + configuredPath);
        }
        if (!Files.isDirectory(candidate)) {
            throw Exceptions.invalidRequest(NOT_A_DIRECTORY + configuredPath);
        }
        if (!Files.isReadable(candidate)) {
            throw Exceptions.invalidRequest(UNREADABLE + configuredPath);
        }

        try {
            return candidate.toRealPath();
        } catch (IOException e) {
            throw Exceptions.invalidRequest(UNREADABLE + configuredPath);
        }
    }
}
