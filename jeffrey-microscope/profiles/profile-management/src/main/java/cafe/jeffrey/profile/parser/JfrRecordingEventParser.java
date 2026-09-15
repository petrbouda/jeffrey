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

package cafe.jeffrey.profile.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.jfrparser.jdk.EventProcessor;
import cafe.jeffrey.jfrparser.jdk.JdkRecordingIterators;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class JfrRecordingEventParser implements RecordingEventParser {

    private static final Logger LOG = LoggerFactory.getLogger(JfrRecordingEventParser.class);

    private static final String SOURCES_DIR = "sources";

    /**
     * From how many source files on a recording is parsed file by file instead of being split into
     * chunks first.
     * <p>
     * Twice the CPU count rather than exactly it: files are wildly uneven — a session's last chunk
     * is whatever was open when the profiler stopped — so having only as many files as workers
     * leaves the parse waiting on its longest one. The extra factor buys enough of them that a
     * slow file overlaps with others instead of being the tail.
     */
    static final int DEFAULT_CHUNK_SPLIT_THRESHOLD = Runtime.getRuntime().availableProcessors() * 2;

    private final TempDirFactory tempDirFactory;
    private final Lz4Compressor lz4Compressor;
    private final int chunkSplitThreshold;

    public JfrRecordingEventParser(TempDirFactory tempDirFactory, Lz4Compressor lz4Compressor) {
        this(tempDirFactory, lz4Compressor, DEFAULT_CHUNK_SPLIT_THRESHOLD);
    }

    JfrRecordingEventParser(TempDirFactory tempDirFactory, Lz4Compressor lz4Compressor, int chunkSplitThreshold) {
        this.tempDirFactory = tempDirFactory;
        this.lz4Compressor = lz4Compressor;
        this.chunkSplitThreshold = chunkSplitThreshold;
    }

    /**
     * Parses every file of the recording into one writer.
     * <p>
     * The files are never joined. Each is expanded into parse units — itself, or its chunks — and
     * every unit is read independently; the events carry absolute timestamps and each unit gets its
     * own writer, so nothing depends on the order they are read in or on their being read together.
     * <p>
     * Sources are expanded concurrently on virtual threads while the units they produce are parsed
     * on the bulk pool. The two must not share a pool: expanding waits for nothing, but the join at
     * the end does, and an expander occupying a bulk thread while the parses it queued sit behind it
     * would be waiting on itself.
     */
    @Override
    public void start(EventWriter eventWriter, RecordingSources sources) {
        try (TempDirectory tempDir = tempDirFactory.newTempDir()) {
            LOG.info("Created the profile's temporary folder: {}", tempDir.path());

            Supplier<EventProcessor<Void>> eventProcessor =
                    () -> new JfrEventReader(eventWriter.newSingleThreadedWriter());

            SourceParseMode mode = SourceParseMode.of(sources, chunkSplitThreshold, lz4Compressor);
            List<Path> files = sources.files();

            LOG.info("Parsing recording: source_count={} mode={} chunk_split_threshold={}",
                    files.size(), mode.getClass().getSimpleName(), chunkSplitThreshold);

            // Written from every expander thread, read once they have all finished.
            List<CompletableFuture<Void>> parsing = Collections.synchronizedList(new ArrayList<>());

            try {
                expandAll(files, mode, tempDir, parsing, eventProcessor);
            } catch (RuntimeException e) {
                // The units already submitted are still writing into this profile's database. Let
                // them finish before the failure unwinds, so nothing is still appending to it while
                // the temp directory is deleted and the initialization is torn down.
                awaitQuietly(parsing);
                throw e;
            }

            await(parsing);
        }
    }

    /**
     * Expands every source concurrently, submitting each unit to be parsed as soon as it appears.
     * Returns once every source has been expanded — not once the parsing has finished.
     */
    private static void expandAll(
            List<Path> files,
            SourceParseMode mode,
            TempDirectory tempDir,
            List<CompletableFuture<Void>> parsing,
            Supplier<EventProcessor<Void>> eventProcessor) {

        List<CompletableFuture<Void>> expanding = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            Path source = files.get(index);

            // A scratch directory per source. Chunk files are named by their position within one
            // recording, so a shared directory would have the second source overwrite the first
            // one's chunk_0 -- and the profile would silently be missing everything it held.
            Path scratchDir = tempDir.path().resolve(SOURCES_DIR).resolve(String.valueOf(index));

            expanding.add(CompletableFuture.runAsync(
                    () -> mode.expand(source, scratchDir,
                            unit -> parsing.add(JdkRecordingIterators.parseAsync(unit, eventProcessor.get()))),
                    Schedulers.sharedVirtual()));
        }

        await(expanding);
    }

    private static void await(List<CompletableFuture<Void>> futures) {
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private static void awaitQuietly(List<CompletableFuture<Void>> parsing) {
        try {
            await(parsing);
        } catch (RuntimeException suppressed) {
            LOG.debug("A chunk parse also failed while unwinding a failed disassembly", suppressed);

            // At DEBUG this failure is invisible in practice, and it is the second of two: whatever
            // ends up reported to the user is the disassembly error, not this. Recording it keeps the
            // real first cause reachable when the reported one turns out to be a symptom.
            Notifications.of(NotificationType.RECORDING_CHUNK_FAILURE_SWALLOWED)
                    .errorType(suppressed)
                    .emit();
        }
    }
}
