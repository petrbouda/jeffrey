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

package cafe.jeffrey.otlpparser;

/**
 * Jeffrey's on-disk convention for OpenTelemetry profiles ({@code .otlp} files).
 * <p>
 * There is no standardized file format for OTLP profiles (the signal is network-first), so Jeffrey
 * defines one: an optional header ({@link #MAGIC} followed by a little-endian {@code u32} format
 * version) and then a sequence of <em>length-delimited</em> {@code ProfilesData} protobuf messages
 * (each frame is one export batch carrying its own dictionary). Because every frame is
 * self-contained, merging rotated files is plain byte concatenation of their frame sequences.
 * <p>
 * Files without the header are treated as a single raw serialized {@code ProfilesData} /
 * {@code ExportProfilesServiceRequest} message (the two are wire-compatible: both have
 * {@code resource_profiles = 1} and {@code dictionary = 2}), so payload dumps produced by an
 * OpenTelemetry Collector debug exporter or async-profiler's OTLP output can be uploaded as-is.
 */
public final class OtlpFileFormat {

    /**
     * Header magic of Jeffrey's framed {@code .otlp} files.
     */
    public static final byte[] MAGIC = {'O', 'T', 'L', 'P'};

    /**
     * Current version of the framed file format, written as little-endian {@code u32} after the magic.
     */
    public static final int VERSION = 1;

    /**
     * Total header size in bytes: 4 magic bytes + 4 version bytes.
     */
    public static final int HEADER_SIZE = MAGIC.length + Integer.BYTES;

    private OtlpFileFormat() {
    }

    /**
     * @return {@code true} if the given buffer starts with the {@link #MAGIC} bytes
     */
    public static boolean startsWithMagic(byte[] header, int length) {
        if (length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (header[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the little-endian {@code u32} version that follows the magic bytes.
     */
    public static int readVersion(byte[] header) {
        int offset = MAGIC.length;
        return (header[offset] & 0xFF)
                | ((header[offset + 1] & 0xFF) << 8)
                | ((header[offset + 2] & 0xFF) << 16)
                | ((header[offset + 3] & 0xFF) << 24);
    }
}
