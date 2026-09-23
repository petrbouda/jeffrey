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
 * When a file was opened by whatever wrote it, which is the timestamp a session is ordered by:
 * Microscope's chunk-window selection tiles a recording with it, retention ages files by it, and
 * which chunk is still open is a maximum over it.
 *
 * <p>Where that answer comes from is a property of the file's type, which is why a
 * {@link HubManagedFile} carries one of these. A JFR written by async-profiler states the
 * instant in its own name and is the only kind that does; everything else has to be asked of the
 * filesystem.
 *
 * <p>The distinction is not cosmetic. A name survives the file being rewritten and a filesystem
 * timestamp does not: compressing a recording produces a new file whose creation time is the
 * moment of compression, minutes or hours after the profiler opened the one it was made from. A
 * type that cannot state its own timestamp therefore must not be rewritten — see
 * {@link Compression}.
 */
@FunctionalInterface
public interface TimestampResolver {

    /**
     * When the file was opened. Never {@code null}: a resolver that cannot read the answer out of
     * the name asks the filesystem rather than failing, because a caller that cannot describe a
     * file leaves it out of the listing altogether.
     */
    Instant resolve(Path file, Supplier<BasicFileAttributes> attributes);

    /**
     * For a caller that has not read the file's attributes — the listing has, and passes them,
     * so that a file is stat'ed once rather than once per question asked of it. Supplied lazily:
     * a name that states its own timestamp never asks the filesystem at all.
     */
    default Instant resolve(Path file) {
        return resolve(file, () -> FileSystemUtils.readAttributes(file));
    }

    /**
     * The file's creation time as the filesystem reports it — right for a file nothing has
     * rewritten, and only for such a file: an archive's creation time is when the archive was
     * written, not when the recording inside it was opened.
     */
    TimestampResolver FILESYSTEM = (file, attributes) -> attributes.get().creationTime().toInstant();

    /** The instant async-profiler wrote into the file's own name, falling back to the filesystem. */
    TimestampResolver RECORDING_NAME = new RecordingNameTimestamp();
}
