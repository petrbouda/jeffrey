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

package cafe.jeffrey.shared.volume.recording;

import java.util.Arrays;
import java.util.Optional;

/**
 * The two forms a recording chunk takes on the volume: the JFR async-profiler writes, and the LZ4
 * archive the hub's compression job turns it into. Every other file (logs, heap dumps, the
 * profiler's scratch file) is listed and served as it lies; a reader classifies those names itself.
 *
 * <p>A recording reads its timestamp from its own name and drops its extension for its id, so
 * {@code profile-1.jfr} and {@code profile-1.jfr.lz4} are one id with one timestamp. Any other file
 * keeps its whole name as id and the volume's creation time as timestamp.
 */
public enum RecordingFormat {
    JFR_LZ4(RecordingFormat.JFR_LZ4_EXTENSION),
    JFR(RecordingFormat.JFR_EXTENSION);

    private static final String JFR_EXTENSION = "jfr";
    private static final String JFR_LZ4_EXTENSION = "jfr.lz4";
    private static final String EXTENSION_SEPARATOR = ".";

    private final String fileExtension;

    RecordingFormat(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /**
     * The recording form of a file, or empty for any other file. The two suffixes are disjoint, so
     * the order of the constants does not matter.
     */
    public static Optional<RecordingFormat> of(String filename) {
        return Arrays.stream(values())
                .filter(format -> format.matches(filename))
                .findFirst();
    }

    /**
     * Whether a file of this name is of this form. Case-sensitive, matching how {@link #idOf} and
     * the timestamp parse strip the suffix.
     */
    public boolean matches(String filename) {
        return filename != null && filename.endsWith(suffix());
    }

    /** Whether this is the compressed form the compression job produces. */
    public boolean isArchive() {
        return this == JFR_LZ4;
    }

    /** Both forms read their timestamp from their own name, which survives compression. */
    public TimestampResolver timestampResolver() {
        return TimestampResolver.RECORDING_NAME;
    }

    /** The file's id within its session: its name without the extension, shared with its archive. */
    public String idOf(String filename) {
        String suffix = suffix();
        return filename.endsWith(suffix) ? filename.substring(0, filename.length() - suffix.length()) : filename;
    }

    private String suffix() {
        return EXTENSION_SEPARATOR + fileExtension;
    }
}
