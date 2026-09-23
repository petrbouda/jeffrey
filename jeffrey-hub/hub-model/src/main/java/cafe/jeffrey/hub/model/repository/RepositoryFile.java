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

package cafe.jeffrey.hub.model.repository;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One file of a recording session, as a repository reports it.
 *
 * <p>A file on its own carries no status. Whether it is still being written is a fact about its
 * session — the profiler holds one chunk open, and only while the session records — so the
 * question is asked of {@link RecordingSession}, which knows both. Carrying the answer here
 * instead meant every producer of a listing had to work it out and every consumer had to trust
 * that it had.
 *
 * <p>Nor does it carry a type. The hub, which lists a session, and Microscope, which reads the
 * listing, each classify a file's name with an enum of their own — the hub's says what it does to
 * the file, Microscope's says what it can read — and the one fact they must agree on is whether
 * the file is a recording, because that is what a download takes and a session's window spans.
 * That fact travels as a flag; everything else either side wants it takes from the name.
 *
 * @param id        the file's identity within its session: its own name with the recording
 *                  extension stripped, so it survives the hub compressing the file
 * @param name      the file's name, relative to the session directory
 * @param createdAt when the profiler opened the file — the timestamp in its own name for a chunk
 *                  that follows the naming convention, its filesystem creation time otherwise
 * @param size      the file's size in bytes — a file whose size cannot be read is left out of
 *                  the listing altogether rather than described with a size it does not have
 * @param recording whether the file is a recording chunk rather than an artifact beside one
 * @param filePath  the absolute path of the file on this hub's volume
 */
public record RepositoryFile(
        String id,
        String name,
        Instant createdAt,
        long size,
        boolean recording,
        Path filePath) {

    public boolean isRecordingFile() {
        return recording;
    }

    /**
     * Whether the file holds anything at all.
     *
     * <p>A profiler stopped before it wrote an event — a container killed, a shutdown that was
     * not graceful — leaves a zero-byte recording behind, and a recording of no bytes is not
     * something any reader can parse. The hub refuses to serve one and a download must not ask
     * for one; the question is asked here so the two cannot come to different answers.
     *
     * <p>Worth asking of a recording only — an empty log is an answer, and a reader wanting
     * one wants to be handed the nothing it holds.
     */
    public boolean hasContent() {
        return size > 0;
    }
}
