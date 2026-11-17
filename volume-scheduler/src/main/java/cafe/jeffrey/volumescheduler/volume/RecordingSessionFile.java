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

package cafe.jeffrey.volumescheduler.volume;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One file of a recording session: a recording chunk or an artifact beside one.
 *
 * <p>A file on its own carries no status. Whether it may still be on its way onto the volume is a
 * fact about its session — only the newest recording of an instance's newest session can be — so
 * the question is asked of {@link RecordingSession}, which knows both. Nor does it carry a type:
 * the one fact the jobs need beyond the name is whether the file is a recording
 * ({@code RecordingFormat} in {@code shared/volume} decides it). Only a recording is compressed,
 * trimmed or counted as a session's data.
 *
 * @param id        the file's identity within its session: its own name with the recording
 *                  extension stripped, so it survives the file being compressed
 * @param name      the file's name, relative to the session directory
 * @param createdAt when the profiler opened the file — the timestamp in its own name for a chunk
 *                  that follows the naming convention, its filesystem creation time otherwise
 * @param size      the file's size in bytes — a file whose size cannot be read is left out of
 *                  the listing altogether rather than described with a size it does not have
 * @param recording whether the file is a recording chunk rather than an artifact beside one
 * @param path      the absolute path of the file on this machine's mount of the volume
 */
public record RecordingSessionFile(
        String id,
        String name,
        Instant createdAt,
        long size,
        boolean recording,
        Path path) {
}
