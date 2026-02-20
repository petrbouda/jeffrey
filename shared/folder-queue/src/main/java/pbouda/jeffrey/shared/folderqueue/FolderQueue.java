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

package pbouda.jeffrey.shared.folderqueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A filesystem-based event queue that uses a flat directory of JSON files.
 * <p>
 * <b>Producer</b> (CLI side) writes event files into a queue directory via {@link #publish}.
 * <b>Consumer</b> (Jeffrey side) reads and processes files via {@link #poll},
 * acknowledges them via {@link #acknowledge} (moves to {@code .processed/}),
 * and periodically cleans up old processed files via {@link #cleanup}.
 * <p>
 * The queue is generic — it stores and retrieves raw String content with
 * a pluggable readiness check ({@link FolderQueueEntryParser}).
 */
public class FolderQueue {

    private static final Logger LOG = LoggerFactory.getLogger(FolderQueue.class);

    private static final String PROCESSED_DIR = ".processed";

    private final Path queueDir;
    private final Clock clock;

    public FolderQueue(Path queueDir, Clock clock) {
        this.queueDir = queueDir;
        this.clock = clock;
        FileSystemUtils.createDirectories(queueDir);
    }

    /**
     * Publishes a new event to the queue directory. Generates a timestamp-sortable
     * filename automatically and writes the content as a single file.
     *
     * @param content  the raw string content to write
     */
    public void publish(String content) {
        String filename = FolderQueueFilename.generate(clock);
        Path filePath = queueDir.resolve(filename);

        try {
            Files.writeString(filePath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to publish event to folder queue: " + filePath, e);
        }
    }

    /**
     * Polls the queue directory for pending event files, parsing each one
     * with the provided parser. Files that the parser cannot parse (returns
     * {@link Optional#empty()}) are silently skipped and will be retried
     * on the next poll.
     *
     * @param parser   the parser/readiness check
     * @param <T>      the type of the parsed result
     * @return a list of successfully parsed entries, sorted chronologically by filename
     */
    public <T> List<FolderQueueEntry<T>> poll(FolderQueueEntryParser<T> parser) {
        if (!Files.isDirectory(queueDir)) {
            return List.of();
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(queueDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(FileSystemUtils::isNotHidden)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOG.error("Failed to list files in queue directory: {}", queueDir, e);
            return List.of();
        }

        List<FolderQueueEntry<T>> entries = new ArrayList<>();
        for (Path file : files) {
            String filename = file.getFileName().toString();
            try {
                String content = Files.readString(file);
                Optional<T> parsed = parser.parse(file, content);
                parsed.ifPresent(value ->
                        entries.add(new FolderQueueEntry<>(file, filename, value)));
            } catch (IOException e) {
                LOG.warn("Failed to read queue file, skipping: {}", file, e);
            }
        }

        return entries;
    }

    /**
     * Acknowledges a processed event file by moving it to the {@code .processed/}
     * subdirectory within the queue directory.
     *
     * @param eventFilePath the path to the event file to acknowledge
     */
    public void acknowledge(Path eventFilePath) {
        Path processedDir = eventFilePath.getParent().resolve(PROCESSED_DIR);
        FileSystemUtils.createDirectories(processedDir);

        Path target = processedDir.resolve(eventFilePath.getFileName());
        try {
            Files.move(eventFilePath, target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to acknowledge event file: " + eventFilePath, e);
        }
    }

    /**
     * Cleans up old processed files. Parses the timestamp from each filename
     * and deletes files older than the given retention duration.
     *
     * @param retainProcessed how long to keep processed files
     */
    public void cleanup(Duration retainProcessed) {
        Path processedDir = queueDir.resolve(PROCESSED_DIR);
        if (!Files.isDirectory(processedDir)) {
            return;
        }

        Instant cutoff = clock.instant().minus(retainProcessed);

        try (Stream<Path> stream = Files.list(processedDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String filename = file.getFileName().toString();
                            Instant fileTimestamp = FolderQueueFilename.parseTimestamp(filename);
                            if (fileTimestamp.isBefore(cutoff)) {
                                Files.delete(file);
                                LOG.debug("Deleted expired processed file: {}", file);
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to cleanup processed file: {}", file, e);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Failed to list processed directory for cleanup: {}", processedDir, e);
        }
    }
}
