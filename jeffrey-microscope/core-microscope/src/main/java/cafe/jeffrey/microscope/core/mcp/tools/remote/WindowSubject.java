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
package cafe.jeffrey.microscope.core.mcp.tools.remote;

import cafe.jeffrey.microscope.core.mcp.tools.ByteSizes;
import cafe.jeffrey.microscope.model.repository.ChunkWindow.Selection.Chunk;
import cafe.jeffrey.microscope.model.repository.RecordingSession;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The session a window is chosen in, as the remote described it when it was read: its span — up to the
 * end of its recorded data — its size and its chunks.
 *
 * @param session the session with its files
 * @param remote  the remote's name, for the question's wording; null on a call that is not asked
 * @param project the project's name, likewise
 * @param now     when the session was read
 */
public record WindowSubject(RecordingSession session, String remote, String project, Instant now) {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

    public WindowSubject {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(now, "now");
    }

    /** Where the session started; null when the remote did not record it. */
    public Instant start() {
        return session.createdAt();
    }

    /**
     * Where its recorded data ends, or its start while it has none; now only without either. While
     * the newest session's end is unmeasured — its data ends where its newest chunk starts, as with
     * a single chunk — that chunk may reach up to now: ending at its start would leave every recent
     * window covering nothing.
     */
    public Instant end() {
        if (mayGrow() && endIsUnmeasured() && now.isAfter(session.lastDataAt())) {
            return now;
        }
        if (session.lastDataAt() != null) {
            return session.lastDataAt();
        }
        return session.createdAt() != null ? session.createdAt() : now;
    }

    private boolean endIsUnmeasured() {
        if (session.lastDataAt() == null) {
            return false;
        }
        List<Chunk> chunks = chunks().chunks();
        return !chunks.isEmpty() && !session.lastDataAt().isAfter(chunks.getLast().start());
    }

    /** Whether it may still grow: no newer session has started in its instance. */
    public boolean mayGrow() {
        return session.newest();
    }

    /** The session's chunks: their spans, their length, the first, the latest and the largest. */
    public SessionChunks chunks() {
        return SessionChunks.of(session);
    }

    /** How long the session has lasted, in whole minutes rounded up; null with no recorded start. */
    Long spanMinutes() {
        if (start() == null) {
            return null;
        }
        long millis = Math.max(0, Duration.between(start(), end()).toMillis());
        return Math.max(1, (millis + ONE_MINUTE.toMillis() - 1) / ONE_MINUTE.toMillis());
    }

    String size() {
        return ByteSizes.format(session.totalSizeBytes());
    }

    String describe() {
        return "Session " + session.name() + " (" + session.id() + ") on the remote " + remote + ", project " + project
                + ", " + span() + " "
                + "Downloading all of it moves every chunk across the network. Which part should Jeffrey download?";
    }

    private String span() {
        if (start() == null) {
            return "has no recorded start and holds " + size() + ".";
        }
        String chunks = chunks().chunkLength()
                .map(length -> ", in chunks of about " + length(length))
                .orElse("");
        if (mayGrow()) {
            return "spans " + start() + " to " + end() + ", the end of its recorded data so far, and is its "
                    + "instance's newest session (" + length(Duration.between(start(), end())) + " so far, "
                    + size() + chunks + ").";
        }
        return "spans " + start() + " to " + end() + " (" + length(Duration.between(start(), end())) + ", "
                + size() + chunks + ").";
    }

    static String length(Duration elapsed) {
        if (elapsed.toHours() > 0) {
            return elapsed.toHours() + "h" + elapsed.toMinutesPart() + "m";
        }
        if (elapsed.toMinutes() > 0) {
            return elapsed.toMinutes() + "m" + elapsed.toSecondsPart() + "s";
        }
        return elapsed.toSeconds() + "s";
    }
}
