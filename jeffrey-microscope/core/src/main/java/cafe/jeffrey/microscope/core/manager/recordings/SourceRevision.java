/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.recordings;

import java.util.regex.Pattern;

/**
 * The source revision a recording's build came from, and where Jeffrey learnt it.
 *
 * @param commit the commit, a 7- to 64-digit hex id (a short or full Git SHA-1 or SHA-256)
 * @param source where it was read: a tag on the recording, or a system property the JVM ran with
 * @param dirty  whether the working tree had uncommitted changes when the build was made; null when
 *               nothing said
 * @param branch the branch the build was made on; null when nothing said
 */
public record SourceRevision(String commit, Source source, Boolean dirty, String branch) {

    private static final Pattern COMMIT = Pattern.compile("[0-9a-fA-F]{7,64}");

    /** Where a revision was read. */
    public enum Source {
        /** A tag on the recording: given at import, or written by the pipeline that made it. */
        TAG,
        /** A {@code -Dgit.commit=…} system property the profiled JVM ran with. */
        SYSTEM_PROPERTY
    }

    public SourceRevision {
        commit = requireCommit(commit);
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        branch = branch == null || branch.isBlank() ? null : branch.strip();
    }

    /**
     * The commit, trimmed, when it is a Git object id; refused otherwise, naming what was given.
     */
    public static String requireCommit(String commit) {
        String trimmed = commit == null ? "" : commit.strip();
        if (!COMMIT.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("commit must be a Git commit id - 7 to 64 hex digits, as "
                    + "git rev-parse HEAD prints it - not '" + commit + "'");
        }
        return trimmed;
    }

    /** Whether a value is a commit id this record would take. */
    static boolean isCommit(String value) {
        return value != null && COMMIT.matcher(value.strip()).matches();
    }
}
