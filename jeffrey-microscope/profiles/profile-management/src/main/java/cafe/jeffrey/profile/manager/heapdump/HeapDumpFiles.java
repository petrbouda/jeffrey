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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where a heap dump profile keeps its dump and everything built from it: one directory inside the
 * profile, holding the copied dump, its index and the cached reports.
 */
public final class HeapDumpFiles {

    /** The directory under a profile's own directory. */
    public static final String ANALYSIS_DIR = "heap-dump";

    private static final Set<ManagedFile> DUMP_TYPES = Set.of(ManagedFile.HEAP_DUMP, ManagedFile.HEAP_DUMP_GZ);

    private final Path analysisDir;

    public HeapDumpFiles(Path analysisDir) {
        this.analysisDir = analysisDir;
    }

    /** The heap dump files of the profile whose own directory this is. */
    public static HeapDumpFiles inProfileDir(Path profileDir) {
        return new HeapDumpFiles(profileDir.resolve(ANALYSIS_DIR));
    }

    public Path analysisDir() {
        return analysisDir;
    }

    /**
     * The dump, or empty when the directory holds none. A gzipped dump that has been decompressed
     * leaves both files; the plain one sorts first and is the one analysed either way.
     */
    public Optional<Path> dumpPath() {
        if (!Files.isDirectory(analysisDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(analysisDir)) {
            return files
                    .filter(file -> DUMP_TYPES.contains(ManagedFile.of(file)))
                    .min(Comparator.comparing(file -> file.getFileName().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list the heap dump directory: " + analysisDir, e);
        }
    }

    public boolean dumpExists() {
        return dumpPath().isPresent();
    }
}
