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

import java.nio.file.Path;

/**
 * A validated handle on the source folder an advisor run reads. The folder belongs to the user, not to
 * Jeffrey: this record is deliberately <strong>not</strong> {@link AutoCloseable}, so no future
 * {@code try (…)} can be read as ownership and nothing here can grow a cleanup step that deletes
 * somebody's working copy.
 *
 * <p>{@code resolvedRef} is the commit the folder is currently on, when it is a git checkout at all.
 * Every claim the model makes about this source is only true of that revision, so it travels with the
 * result. When it is unknown, the UI says so rather than implying the source matched the recording —
 * an unverifiable match is worse than an admitted gap.</p>
 *
 * @param root            the canonical, symlink-free path to the source folder
 * @param resolvedRef     the commit the working tree is on, or null when the folder is not a git checkout
 * @param recordingRef    the commit the recording was captured from, or null when it carries no build metadata
 */
public record SourceTree(Path root, String resolvedRef, String recordingRef) {

    public SourceTree {
        if (root == null) {
            throw new IllegalArgumentException("Source tree root must not be null");
        }
    }

    public boolean hasResolvedRef() {
        return resolvedRef != null && !resolvedRef.isBlank();
    }

    /**
     * True when both refs are known and disagree — the source on disk is not the source that produced
     * the profile. Reported to the user as a warning rather than enforced: the working copy is often
     * a few commits ahead and the analysis is still useful.
     */
    public boolean refMismatch() {
        return hasResolvedRef()
                && recordingRef != null
                && !recordingRef.isBlank()
                && !resolvedRef.startsWith(recordingRef)
                && !recordingRef.startsWith(resolvedRef);
    }
}
