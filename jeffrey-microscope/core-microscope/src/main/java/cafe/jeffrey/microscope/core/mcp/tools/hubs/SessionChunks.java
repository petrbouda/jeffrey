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

import cafe.jeffrey.hub.client.RepositoryFiles;
import cafe.jeffrey.microscope.model.repository.ChunkWindow;
import cafe.jeffrey.microscope.model.repository.ChunkWindow.Selection.Chunk;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A session's finished chunks with their spans — each ends where the next begins — and what the
 * predefined windows read from them.
 * <p>
 * The chunk length is the profiler's own configuration, which never reaches the hub, so it is
 * measured: the median spacing of consecutive chunk starts. Within one session the chunks are equal,
 * and a median stays put when one chunk is missing, the last one is cut short, or the profiler
 * started a moment late.
 *
 * @param session the session
 * @param chunks  its finished chunks, oldest first
 */
public record SessionChunks(RecordingSession session, List<Chunk> chunks) {

    public SessionChunks {
        chunks = List.copyOf(chunks);
    }

    static SessionChunks of(RecordingSession session) {
        Set<String> finished = session.finishedRecordings().stream()
                .map(RepositoryFile::id)
                .collect(Collectors.toSet());
        return new SessionChunks(session, ChunkWindow.ofFiles(session, finished).chunks());
    }

    /** Whether the session's first chunk is still on the hub. */
    public sealed interface Startup {

        /** The first chunk is there. */
        record Kept(Chunk chunk) implements Startup {
        }

        /** The hub's cleaners removed it: the oldest chunk kept starts more than one chunk later. */
        record Gone(Chunk oldest, Duration chunkLength) implements Startup {
        }

        /** It cannot be told: no recorded start, or no finished chunk. */
        record Unknown(String why) implements Startup {
        }
    }

    /**
     * The largest compressed chunk.
     *
     * @param chunk              the chunk
     * @param medianSizeBytes    the median size of the compressed chunks it was compared with
     * @param compared           how many compressed chunks were compared
     * @param tied               whether another chunk is as large; the most recent of them is taken
     * @param notCompressed      finished chunks left out because they are not compressed yet
     */
    public record Peak(Chunk chunk, long medianSizeBytes, int compared, boolean tied, int notCompressed) {
    }

    /** The median spacing of consecutive chunk starts; empty while there is nothing to measure. */
    public Optional<Duration> chunkLength() {
        List<Instant> starts = new ArrayList<>(chunks.stream().map(Chunk::start).toList());
        session.openRecording().map(RepositoryFile::createdAt).ifPresent(starts::add);
        List<Long> spacings = new ArrayList<>();
        for (int i = 1; i < starts.size(); i++) {
            spacings.add(Duration.between(starts.get(i - 1), starts.get(i)).toMillis());
        }
        if (!spacings.isEmpty()) {
            return Optional.of(Duration.ofMillis(lowerMedian(spacings)));
        }
        // One chunk and nothing after it: its own span, when the session's end gives it one.
        return chunks.stream()
                .filter(chunk -> chunk.end() != null)
                .findFirst()
                .map(chunk -> Duration.between(chunk.start(), chunk.end()));
    }

    /**
     * Whether the first chunk is kept: the oldest finished chunk starts no more than one chunk
     * length after the session did.
     */
    public Startup startup() {
        if (session.createdAt() == null) {
            return new Startup.Unknown("the session has no recorded start");
        }
        if (chunks.isEmpty()) {
            return new Startup.Unknown("the session has no finished chunk yet");
        }
        Chunk first = chunks.getFirst();
        Optional<Duration> length = chunkLength();
        if (length.isEmpty()) {
            return new Startup.Kept(first);
        }
        Duration gap = Duration.between(session.createdAt(), first.start());
        return gap.compareTo(length.get()) <= 0
                ? new Startup.Kept(first)
                : new Startup.Gone(first, length.get());
    }

    /** The newest finished chunk that holds anything; never the one still being written. */
    public Optional<Chunk> latest() {
        for (int i = chunks.size() - 1; i >= 0; i--) {
            Chunk chunk = chunks.get(i);
            if (hasBytes(chunk)) {
                return Optional.of(chunk);
            }
        }
        return Optional.empty();
    }

    /**
     * The largest finished chunk among the LZ4-compressed ones: a size on disk compares only with
     * another of the same format, and a chunk the hub has not compressed yet is left out until it is.
     */
    public Optional<Peak> peak() {
        List<Chunk> compressed = chunks.stream()
                .filter(SessionChunks::hasBytes)
                .filter(chunk -> RepositoryFiles.typeOf(chunk.file()) == ManagedFile.JFR_LZ4)
                .toList();
        if (compressed.isEmpty()) {
            return Optional.empty();
        }
        long largest = compressed.stream().mapToLong(chunk -> chunk.file().size()).max().orElseThrow();
        List<Chunk> atLargest = compressed.stream().filter(chunk -> chunk.file().size() == largest).toList();
        Chunk peak = atLargest.stream().max(Comparator.comparing(Chunk::start)).orElseThrow();
        long median = lowerMedian(compressed.stream().map(chunk -> chunk.file().size()).toList());
        int notCompressed = (int) chunks.stream()
                .filter(SessionChunks::hasBytes)
                .filter(chunk -> RepositoryFiles.typeOf(chunk.file()) != ManagedFile.JFR_LZ4)
                .count();
        return Optional.of(new Peak(peak, median, compressed.size(), atLargest.size() > 1, notCompressed));
    }

    private static boolean hasBytes(Chunk chunk) {
        Long size = chunk.file().size();
        return size != null && size > 0;
    }

    /** The lower of the two middle values for an even count, so the answer is one that occurred. */
    private static long lowerMedian(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get((sorted.size() - 1) / 2);
    }
}
