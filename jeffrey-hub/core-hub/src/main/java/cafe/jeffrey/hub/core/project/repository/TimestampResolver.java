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

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * When a file was opened by whatever wrote it: what chunk-window selection, retention and the
 * open-chunk check order by. A recording reads it from its name, which survives compression; any
 * other file asks the filesystem, which is why only recordings may be compressed.
 */
@FunctionalInterface
public interface TimestampResolver {

    /** When the file was opened; never {@code null} — falls back to the filesystem rather than failing. */
    Instant resolve(Path file, Supplier<BasicFileAttributes> attributes);

    /** For a caller without the file's attributes at hand; they are read only if needed. */
    default Instant resolve(Path file) {
        return resolve(file, () -> FileSystemUtils.readAttributes(file));
    }

    /** The file's creation time as the filesystem reports it; wrong for a rewritten file. */
    TimestampResolver FILESYSTEM = (file, attributes) -> attributes.get().creationTime().toInstant();

    /** The instant async-profiler wrote into the file's own name, falling back to the filesystem. */
    TimestampResolver RECORDING_NAME = new RecordingNameTimestamp();
}
