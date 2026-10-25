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

import cafe.jeffrey.shared.volume.VolumeEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * When a file was opened by whatever wrote it: what chunk-window selection, retention and the
 * open-chunk check order by. A recording reads it from its name, which survives compression; any
 * other file takes the volume's creation time, which is why only recordings may be compressed.
 */
@FunctionalInterface
public interface TimestampResolver {

    /**
     * When the file was opened — falling back to the volume rather than failing — or empty when the
     * name says nothing and the volume's description carried no time, which a caller settles with
     * one more look at the file.
     *
     * @param file the file as the volume described it
     * @param zone the time zone of the machine that wrote the session, which a timestamp in a file's
     *             name is local to
     */
    Optional<Instant> resolve(VolumeEntry file, ZoneId zone);

    /** The file's creation time as the volume reports it, if it did; wrong for a rewritten file. */
    TimestampResolver VOLUME = (file, zone) -> file.createdAt();

    /** The instant async-profiler wrote into the file's own name, falling back to the volume. */
    TimestampResolver RECORDING_NAME = new RecordingNameTimestamp();
}
