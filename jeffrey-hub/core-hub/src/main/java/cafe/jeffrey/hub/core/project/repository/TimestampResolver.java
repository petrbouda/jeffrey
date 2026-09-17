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

package cafe.jeffrey.hub.core.project.repository;

import java.nio.file.Path;
import java.time.Instant;

/**
 * When a file was opened by whatever wrote it, which is the timestamp a session is ordered by:
 * {@code ChunkWindow} tiles a recording with it, retention ages files by it, and which chunk is
 * still open is a maximum over it.
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
public sealed interface TimestampResolver permits FilesystemTimestamp, RecordingNameTimestamp {

    /**
     * When the file was opened. Never {@code null}: a resolver that cannot read the answer out of
     * the name asks the filesystem rather than failing, because a caller that cannot describe a
     * file leaves it out of the listing altogether.
     */
    Instant resolve(Path file);

    /** The file's creation time as the filesystem reports it. */
    TimestampResolver FILESYSTEM = new FilesystemTimestamp();

    /** The instant async-profiler wrote into the file's own name, falling back to the filesystem. */
    TimestampResolver RECORDING_NAME = new RecordingNameTimestamp();
}
