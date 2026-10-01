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
package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hub sessions of equal chunks for the window tests — any chunk length, finished or still
 * recording, compressed or not, with the oldest chunks removed by a cleaner — built one way so the
 * tests say only what differs.
 */
public final class ChunkedSessions {

    public static final String SESSION_ID = "session-1";
    private static final String COMPRESSED = ".jfr.lz4";
    private static final String RAW = ".jfr";

    private final Instant start;
    private final Duration chunkLength;
    private final List<Long> sizes = new ArrayList<>();
    private final List<Boolean> compressed = new ArrayList<>();
    private int removedOldest;
    private boolean live;
    private Instant profilerStart;

    private ChunkedSessions(Instant start, Duration chunkLength) {
        this.start = start;
        this.chunkLength = chunkLength;
        this.profilerStart = start;
    }

    /** A session started at {@code start} whose profiler rolls a chunk every {@code chunkLength}. */
    public static ChunkedSessions startingAt(Instant start, Duration chunkLength) {
        return new ChunkedSessions(start, chunkLength);
    }

    /** Finished chunks of these sizes, compressed, oldest first. */
    public ChunkedSessions compressed(long... bytes) {
        for (long size : bytes) {
            sizes.add(size);
            compressed.add(true);
        }
        return this;
    }

    /** Finished chunks of these sizes, not compressed yet, after the others. */
    public ChunkedSessions raw(long... bytes) {
        for (long size : bytes) {
            sizes.add(size);
            compressed.add(false);
        }
        return this;
    }

    /** The cleaner removed this many of the oldest chunks. */
    public ChunkedSessions withoutOldest(int chunks) {
        this.removedOldest = chunks;
        return this;
    }

    /** The profiler's first chunk began this long after the session did. */
    public ChunkedSessions profilerLate(Duration lag) {
        this.profilerStart = start.plus(lag);
        return this;
    }

    /** Still recording: one more chunk is open after the finished ones. */
    public ChunkedSessions live() {
        this.live = true;
        return this;
    }

    public Instant chunkStart(int index) {
        return profilerStart.plus(chunkLength.multipliedBy(index));
    }

    /** Where the last finished chunk ends: the session's finish, or the open chunk's start. */
    public Instant end() {
        return chunkStart(sizes.size());
    }

    public static String fileId(int index) {
        return "c" + index;
    }

    public RecordingSession build() {
        List<RepositoryFile> files = new ArrayList<>();
        for (int i = removedOldest; i < sizes.size(); i++) {
            String name = "profile-" + i + (compressed.get(i) ? COMPRESSED : RAW);
            files.add(new RepositoryFile(fileId(i), name, chunkStart(i), sizes.get(i), true, null));
        }
        if (live) {
            files.add(new RepositoryFile(fileId(sizes.size()), "profile-" + sizes.size() + RAW,
                    chunkStart(sizes.size()), 10L, true, null));
        }
        return new RecordingSession(SESSION_ID, "checkout-api", "inst-1", start, live ? null : end(),
                live ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED, null, files, false);
    }

    public WindowSubject subject(Instant now) {
        return new WindowSubject(build(), "production", "checkout", Objects.requireNonNull(now, "now"));
    }
}
