/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.heapdump;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofCopySanitizer;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofInPlaceSanitizer;
import cafe.jeffrey.profile.heapdump.sanitizer.SanitizeMode;
import cafe.jeffrey.profile.heapdump.sanitizer.SanitizeResult;
import cafe.jeffrey.shared.common.model.repository.FileExtensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Simple heap loader that relies on NetBeans' built-in .hwcache mechanism.
 * Each call to load() creates a new Heap object, but subsequent loads are fast
 * because NetBeans caches parsed data in .hwcache directory next to the heap dump.
 */
public class SimpleHeapLoader implements HeapLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleHeapLoader.class);

    private final SanitizeMode defaultMode;

    public SimpleHeapLoader() {
        this(SanitizeMode.IN_PLACE);
    }

    public SimpleHeapLoader(SanitizeMode defaultMode) {
        if (defaultMode == null) {
            throw new IllegalArgumentException("defaultMode must not be null");
        }
        this.defaultMode = defaultMode;
        LOG.debug("SimpleHeapLoader created: defaultSanitizeMode={}", defaultMode);
    }

    /**
     * The default sanitize mode used when callers invoke {@link #sanitize(Path)}
     * without specifying one.
     */
    public SanitizeMode defaultSanitizeMode() {
        return defaultMode;
    }

    @Override
    public Optional<Heap> load(Path heapDumpPath) {
        Path normalizedPath = heapDumpPath.toAbsolutePath().normalize();

        if (!Files.exists(normalizedPath)) {
            LOG.warn("Heap dump file does not exist: path={}", normalizedPath);
            return Optional.empty();
        }

        try {
            String fileName = normalizedPath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(FileExtensions.HPROF_GZ)) {
                // Check if decompressed version exists, use it if available
                Path decompressedPath = resolveDecompressedPath(normalizedPath);
                if (Files.exists(decompressedPath)) {
                    LOG.debug("Found existing decompressed heap dump: path={}", decompressedPath);
                    return loadHeap(decompressedPath);
                }
                // Decompress first, then load
                return decompressAndLoad(normalizedPath, decompressedPath);
            } else if (fileName.endsWith(FileExtensions.HPROF)) {
                return loadHeap(normalizedPath);
            } else {
                LOG.warn("Unsupported heap dump file extension: path={}", normalizedPath);
                return Optional.empty();
            }
        } catch (Exception e) {
            LOG.warn("Failed to load heap dump: path={} error={}", normalizedPath, e.getMessage());
            return Optional.empty();
        }
    }

    private Path resolveDecompressedPath(Path gzippedPath) {
        String gzFileName = gzippedPath.getFileName().toString();
        String decompressedName = gzFileName.substring(0, gzFileName.length() - 3); // Remove ".gz"
        return gzippedPath.resolveSibling(decompressedName);
    }

    private Optional<Heap> loadHeap(Path heapDumpPath) throws IOException {
        // If a non-destructive copy was produced previously, prefer it.
        Path sanitizedPath = resolveSanitizedPath(heapDumpPath);
        if (Files.exists(sanitizedPath)) {
            LOG.debug("Found sanitized heap dump copy, loading: path={}", sanitizedPath);
            return loadHeapDirect(sanitizedPath);
        }
        return loadHeapDirect(heapDumpPath);
    }

    private Optional<Heap> loadHeapDirect(Path heapDumpPath) throws IOException {
        long startNanos = System.nanoTime();
        Heap heap = HeapFactory.createHeap(heapDumpPath.toFile());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        LOG.debug("Heap dump loaded: path={} durationMs={}", heapDumpPath, durationMs);
        return Optional.of(heap);
    }

    /**
     * Sanitizes a corrupted heap dump using the configured default mode (see
     * {@link #defaultSanitizeMode()}).
     */
    public SanitizeResult sanitize(Path heapDumpPath) throws IOException {
        return sanitize(heapDumpPath, defaultMode);
    }

    /**
     * Sanitizes a corrupted heap dump using the explicitly requested mode,
     * overriding the default for this single call.
     *
     * <ul>
     *   <li>{@link SanitizeMode#IN_PLACE} — mutates the original file.</li>
     *   <li>{@link SanitizeMode#COPY} — writes a sibling {@code <name>.sanitized}
     *       file; the original is preserved and {@link #load(Path)} prefers the
     *       sanitized copy on subsequent calls.</li>
     * </ul>
     */
    public SanitizeResult sanitize(Path heapDumpPath, SanitizeMode mode) throws IOException {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        return switch (mode) {
            case IN_PLACE -> {
                Path sanitizedPath = resolveSanitizedPath(heapDumpPath);
                if (Files.exists(sanitizedPath)) {
                    LOG.warn("In-place sanitization with stale .sanitized sibling present — load() will still prefer the sibling: original={} sibling={}",
                            heapDumpPath, sanitizedPath);
                }
                yield HprofInPlaceSanitizer.sanitize(heapDumpPath);
            }
            case COPY -> {
                Path sanitizedPath = resolveSanitizedPath(heapDumpPath);
                yield HprofCopySanitizer.sanitize(heapDumpPath, sanitizedPath);
            }
        };
    }

    private Path resolveSanitizedPath(Path heapDumpPath) {
        return heapDumpPath.resolveSibling(heapDumpPath.getFileName().toString() + ".sanitized");
    }

    private Optional<Heap> decompressAndLoad(Path gzippedPath, Path decompressedPath) throws IOException {
        long startNanos = System.nanoTime();

        // Decompress to the same directory so it can be reused
        try (GZIPInputStream gzis = new GZIPInputStream(Files.newInputStream(gzippedPath))) {
            Files.copy(gzis, decompressedPath);
        }

        long decompressMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        LOG.debug("Heap dump decompressed: path={} durationMs={}", decompressedPath, decompressMs);

        // Remove the compressed file to save disk space
        Files.deleteIfExists(gzippedPath);
        LOG.debug("Removed compressed heap dump file: path={}", gzippedPath);

        return loadHeap(decompressedPath);
    }

    @Override
    public void unload(Path heapDumpPath) {
        // No-op: nothing to unload since we don't cache
        LOG.debug("Unload called (no-op for SimpleHeapLoader): path={}", heapDumpPath);
    }

    @Override
    public boolean isLoaded(Path heapDumpPath) {
        // Always returns false since we don't cache in memory
        return false;
    }
}
