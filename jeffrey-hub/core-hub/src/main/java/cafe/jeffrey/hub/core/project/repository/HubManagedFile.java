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

package cafe.jeffrey.hub.core.project.repository;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * The two kinds of file the hub acts on: a JFR, which it compresses, and the LZ4 archive that
 * produces. Every other file (logs, heap dumps, the profiler's scratch file) is listed and served
 * as it lies; Microscope classifies those names itself, with its own {@code ManagedFile}.
 *
 * <p>A recording reads its timestamp from its own name and drops its extension for its id, so
 * {@code profile-1.jfr} and {@code profile-1.jfr.lz4} are one id with one timestamp. Any other file
 * keeps its whole name as id and the filesystem time as timestamp.
 */
public enum HubManagedFile {
    JFR_LZ4(HubManagedFile.JFR_LZ4_EXTENSION, TimestampResolver.RECORDING_NAME, null),
    JFR(HubManagedFile.JFR_EXTENSION, TimestampResolver.RECORDING_NAME, Compression.LZ4);

    private static final String JFR_EXTENSION = "jfr";
    private static final String JFR_LZ4_EXTENSION = "jfr.lz4";
    private static final String EXTENSION_SEPARATOR = ".";

    private final String fileExtension;
    private final TimestampResolver timestampResolver;
    private final Compression compression;

    HubManagedFile(String fileExtension, TimestampResolver timestampResolver, Compression compression) {
        this.fileExtension = fileExtension;
        this.timestampResolver = timestampResolver;
        this.compression = compression;
    }

    public static Optional<HubManagedFile> of(Path path) {
        return of(path.getFileName().toString());
    }

    /**
     * The recording type of a file, or empty for any other file. The two suffixes are disjoint, so
     * the order of the constants does not matter.
     */
    public static Optional<HubManagedFile> of(String filename) {
        return Arrays.stream(values())
                .filter(type -> type.matches(filename))
                .findFirst();
    }

    /**
     * Whether a file of this name is of this type. Case-sensitive, matching how {@link #idOf} and
     * the timestamp parse strip the suffix.
     */
    public boolean matches(String filename) {
        return filename != null && filename.endsWith(suffix());
    }

    /** Whether this is the compressed form the compression job produces. */
    public boolean isArchive() {
        return this == JFR_LZ4;
    }

    public TimestampResolver timestampResolver() {
        return timestampResolver;
    }

    /** How a file of this type is compressed, or empty for the archive itself. */
    public Optional<Compression> compression() {
        return Optional.ofNullable(compression);
    }

    /** The file's id within its session: its name without the extension, shared with its archive. */
    public String idOf(Path file) {
        String name = file.getFileName().toString();
        String suffix = suffix();
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : name;
    }

    private String suffix() {
        return EXTENSION_SEPARATOR + fileExtension;
    }
}
