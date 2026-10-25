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

package cafe.jeffrey.shared.volume;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * The storage a volume lies on, read through the only questions its readers ask of it.
 * One implementation per backend: a directory tree ({@link FilesystemVolume}) or a file share reached
 * over its API.
 *
 * <p>Every method keeps "nothing is there" apart from "it could not be looked at": the first is an
 * empty answer, the second an {@link UncheckedIOException}. The layout acts on that
 * difference — an unreadable heartbeat must not finish a live session.
 */
public interface VolumeAccess {

    /**
     * The most {@link #read} hands over. Markers and liveness files are a few hundred bytes, and
     * anything that can write to the volume — every profiled workload — could otherwise make a
     * reader hold a file of any size in memory just by listing.
     */
    int MAX_READ_BYTES = 1024 * 1024;

    /**
     * What lies directly under {@code directory}, in no particular order; empty when there is no
     * directory there. An entry that vanishes while it is listed is left out.
     *
     * @throws UncheckedIOException when the directory is there and cannot be listed
     */
    List<VolumeEntry> list(VolumePath directory);

    /**
     * The entry at {@code path}, or empty when nothing is there. Unlike a listing row, it carries a
     * creation time whenever the backend knows any time for the entry.
     *
     * @throws UncheckedIOException when it cannot be looked at
     */
    Optional<VolumeEntry> stat(VolumePath path);

    /**
     * The whole content of a small file — a marker, a liveness file — or empty when there is none.
     * Never reads more than {@link #MAX_READ_BYTES} plus one byte of it.
     *
     * @throws UncheckedIOException when something is there and cannot be read: a directory in the
     *                              file's place, or a file larger than {@link #MAX_READ_BYTES}
     */
    Optional<byte[]> read(VolumePath file);

    /** Hands over what a {@link #read} received, refusing it when it came past the cap. */
    static byte[] withinReadCap(VolumePath file, byte[] content) {
        if (content.length > MAX_READ_BYTES) {
            throw new UncheckedIOException(new IOException(
                    "A volume file is larger than a marker can be: path=%s max_bytes=%d"
                            .formatted(file, MAX_READ_BYTES)));
        }
        return content;
    }

    /**
     * A file opened for streaming, with the name and size it has at the moment it was opened —
     * taken from one handle, so a rewrite of the file cannot slip in between them — or empty when
     * there is no file there.
     *
     * @throws UncheckedIOException when something is there and cannot be opened
     */
    Optional<OpenedFile> open(VolumePath file);

    /**
     * A file being read: what a download hands on, the bytes with the name they were read under.
     *
     * @param name    the file's name as it lies now
     * @param size    bytes, as of opening
     * @param content the bytes; closed with this
     */
    record OpenedFile(String name, long size, InputStream content) implements Closeable {

        public OpenedFile {
            if (name == null || name.isBlank() || size < 0 || content == null) {
                throw new IllegalArgumentException("An opened file needs a name, a size and its content");
            }
        }

        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
