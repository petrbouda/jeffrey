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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * What a {@code recordings_analyzeFile} import in flight is keyed on, so a second call for the same
 * file joins the first rather than copying it in again.
 * <p>
 * The identity is the one the reuse check matches a stored recording by — the file's name and its
 * size — so the two cannot disagree about which calls are "the same file": a call the check would
 * answer from the store once the copy has landed is exactly a call that joins the copy while it has
 * not. A forced import asked for a second profile on purpose and gets a key nothing else shares, and
 * so does a file its name and size cannot identify.
 */
sealed interface ImportKey {

    /**
     * The files whose name and size never tell one from another: every JVM writes its perf counters
     * to a file of the same name and the same fixed size, so the counters of a second JVM would be
     * taken for the first's.
     */
    Set<ManagedFile> NOT_IDENTIFIED_BY_NAME_AND_SIZE = Set.of(ManagedFile.PERF_COUNTERS);

    /** Whether the file's name and size identify it, so an earlier import of it can be reused or joined. */
    static boolean identifiedByNameAndSize(Path recordingPath) {
        return !NOT_IDENTIFIED_BY_NAME_AND_SIZE.contains(ManagedFile.of(recordingPath));
    }

    /**
     * A file by the identity the reuse check uses. The size is read at the call, so a file a new run
     * rewrote to another length is another file.
     */
    record SourceFile(String fileName, long size) implements ImportKey {

        private static final String UNREADABLE_SOURCE = "Cannot read the recording file's size: ";

        public SourceFile {
            if (fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("fileName is required");
            }
            if (size < 0) {
                throw new IllegalArgumentException("size must not be negative: " + size);
            }
        }

        static SourceFile of(Path recordingPath) {
            try {
                return new SourceFile(recordingPath.getFileName().toString(), Files.size(recordingPath));
            } catch (IOException e) {
                throw new IllegalArgumentException(UNREADABLE_SOURCE + recordingPath, e);
            }
        }
    }

    /**
     * An import that never joins or is joined by another call: one made with {@code force=true}, or of
     * a file its name and size cannot identify.
     */
    record Unshared(UUID attempt) implements ImportKey {

        public Unshared {
            if (attempt == null) {
                throw new IllegalArgumentException("attempt is required");
            }
        }

        static Unshared fresh() {
            return new Unshared(UUID.randomUUID());
        }
    }
}
