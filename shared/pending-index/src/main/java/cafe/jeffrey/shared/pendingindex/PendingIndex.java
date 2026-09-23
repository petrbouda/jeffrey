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

package cafe.jeffrey.shared.pendingindex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A flat directory of small files naming work the reader should look at.
 *
 * <p><b>This is an index, not a queue.</b> There is no acknowledgement, no delivery guarantee
 * and no consumer offset. A writer {@link #add}s an entry pointing at something that changed;
 * a reader {@link #list}s the entries, does the real work by reading the actual state those
 * entries point at, and {@link #remove}s an entry once that work succeeded. Entries carry a
 * <em>pointer</em>, never a copy of the state — so the index can never disagree with the state
 * it points at, and losing an entry costs a missed look, not a corrupted view.</p>
 *
 * <p>An entry is removed by deletion, deliberately: keeping processed entries around (in a
 * {@code .processed/} directory, say) turns the folder into a second store of consumption
 * state that then needs its own retention, which is exactly the shape that made the previous
 * event-file mechanism lossy.</p>
 *
 * <p>Content is raw text with a pluggable readiness check ({@link PendingIndexEntryParser}),
 * so a half-written entry is skipped and retried rather than failing the tick.</p>
 */
public class PendingIndex {

    private static final Logger LOG = LoggerFactory.getLogger(PendingIndex.class);

    private final Path indexDir;
    private final Clock clock;

    public PendingIndex(Path indexDir, Clock clock) {
        this.indexDir = indexDir;
        this.clock = clock;
    }

    /**
     * Adds an entry. Creates the index directory on demand, so a writer never has to
     * pre-create it.
     *
     * @param id      the caller-provided identifier embedded in the filename
     * @param content the pointer to record — typically a path relative to the index's scope
     */
    public void add(String id, String content) {
        FileSystemUtils.createDirectories(indexDir);

        String filename = PendingIndexFilename.generate(clock, id);
        Path filePath = indexDir.resolve(filename);

        try {
            Files.writeString(filePath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add pending index entry: " + filePath, e);
        }
    }

    /**
     * Lists pending entries, oldest first. Files the parser rejects are skipped and retried on
     * the next call. A missing index directory is simply empty — the common case when nothing
     * has ever been written for this scope.
     *
     * @param parser the readiness check / content parser
     * @return the parsed entries, chronologically ordered by filename
     */
    public <T> List<PendingIndexEntry<T>> list(PendingIndexEntryParser<T> parser) {
        if (!Files.isDirectory(indexDir)) {
            return List.of();
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(indexDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(FileSystemUtils::isNotHidden)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOG.error("Failed to list pending index directory: index_dir={}", indexDir, e);
            return List.of();
        }

        List<PendingIndexEntry<T>> entries = new ArrayList<>();
        for (Path file : files) {
            String filename = file.getFileName().toString();
            try {
                String content = Files.readString(file);
                Optional<T> parsed = parser.parse(file, content);
                parsed.ifPresent(value -> entries.add(new PendingIndexEntry<>(file, filename, value)));
            } catch (IOException e) {
                LOG.warn("Failed to read pending index entry, skipping: file={}", file, e);
            }
        }
        return entries;
    }

    /**
     * Removes an entry whose work is done. Removing an entry that is already gone is not an
     * error — a reader that crashed after doing the work and before removing it repeats both
     * steps harmlessly on the next tick.
     */
    public void remove(Path entryPath) {
        try {
            Files.deleteIfExists(entryPath);
        } catch (IOException e) {
            LOG.warn("Failed to remove pending index entry, it will be retried: file={}", entryPath, e);
        }
    }
}
