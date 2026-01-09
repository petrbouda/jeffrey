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

package pbouda.jeffrey.profile.heapdump;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.model.repository.FileExtensions;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * A caching heap loader that uses SoftReferences to allow GC to reclaim
 * heap memory under pressure. Also implements idle timeout eviction.
 */
public class CachingHeapLoader implements HeapLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CachingHeapLoader.class);
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final Clock clock;
    private final Duration idleTimeout;
    private final Map<Path, CacheEntry> heapCache;
    private final ScheduledExecutorService evictionScheduler;

    public CachingHeapLoader(Clock clock) {
        this(clock, DEFAULT_IDLE_TIMEOUT);
    }

    public CachingHeapLoader(Clock clock, Duration idleTimeout) {
        this.clock = clock;
        this.idleTimeout = idleTimeout;
        this.heapCache = new ConcurrentHashMap<>();
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heap-cache-eviction");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic eviction check
        long checkIntervalMinutes = Math.max(1, idleTimeout.toMinutes() / 2);
        this.evictionScheduler.scheduleAtFixedRate(
                this::evictIdleEntries,
                checkIntervalMinutes,
                checkIntervalMinutes,
                TimeUnit.MINUTES
        );
    }

    @Override
    public Optional<Heap> load(Path heapDumpPath) {
        Path normalizedPath = heapDumpPath.toAbsolutePath().normalize();

        CacheEntry entry = heapCache.get(normalizedPath);
        if (entry != null) {
            Heap heap = entry.heapRef.get();
            if (heap != null) {
                entry.lastAccess = clock.instant();
                LOG.debug("Returning cached heap: path={}", normalizedPath);
                return Optional.of(heap);
            } else {
                // SoftReference was cleared by GC
                heapCache.remove(normalizedPath);
                LOG.debug("Cached heap was garbage collected, reloading: path={}", normalizedPath);
            }
        }

        // Load the heap
        return loadHeapFromFile(normalizedPath).map(heap -> {
            heapCache.put(normalizedPath, new CacheEntry(heap, clock.instant()));
            return heap;
        });
    }

    private Optional<Heap> loadHeapFromFile(Path heapDumpPath) {
        if (!Files.exists(heapDumpPath)) {
            LOG.warn("Heap dump file does not exist: path={}", heapDumpPath);
            return Optional.empty();
        }

        try {
            String fileName = heapDumpPath.getFileName().toString().toLowerCase();

            if (fileName.endsWith(FileExtensions.HPROF_GZ)) {
                return loadGzippedHeap(heapDumpPath);
            } else if (fileName.endsWith(FileExtensions.HPROF)) {
                return loadPlainHeap(heapDumpPath);
            } else {
                LOG.warn("Unsupported heap dump file extension: path={}", heapDumpPath);
                return Optional.empty();
            }
        } catch (Exception e) {
            LOG.error("Failed to load heap dump: path={}", heapDumpPath, e);
            return Optional.empty();
        }
    }

    private Optional<Heap> loadPlainHeap(Path heapDumpPath) throws IOException {
        LOG.info("Loading heap dump: path={}", heapDumpPath);
        long startTime = System.currentTimeMillis();

        // Use createHeap to load the heap dump
        Heap heap = HeapFactory.createHeap(heapDumpPath.toFile());

        long duration = System.currentTimeMillis() - startTime;
        LOG.info("Heap dump loaded: path={} durationMs={}", heapDumpPath, duration);

        return Optional.of(heap);
    }

    private Optional<Heap> loadGzippedHeap(Path gzippedPath) throws IOException {
        LOG.info("Decompressing and loading gzipped heap dump: path={}", gzippedPath);
        long startTime = System.currentTimeMillis();

        // Create a temporary file for the decompressed heap
        Path tempFile = Files.createTempFile("heap-dump-", FileExtensions.HPROF);
        tempFile.toFile().deleteOnExit();

        try (GZIPInputStream gzis = new GZIPInputStream(Files.newInputStream(gzippedPath))) {
            Files.copy(gzis, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        Heap heap = HeapFactory.createHeap(tempFile.toFile());

        // Note: Don't delete temp file - heap may still need it
        // The file is marked deleteOnExit so it will be cleaned up

        long duration = System.currentTimeMillis() - startTime;
        LOG.info("Gzipped heap dump loaded: path={} durationMs={}", gzippedPath, duration);

        return Optional.of(heap);
    }

    @Override
    public void unload(Path heapDumpPath) {
        Path normalizedPath = heapDumpPath.toAbsolutePath().normalize();
        CacheEntry removed = heapCache.remove(normalizedPath);
        if (removed != null) {
            LOG.info("Heap unloaded from cache: path={}", normalizedPath);
        }
    }

    @Override
    public boolean isLoaded(Path heapDumpPath) {
        Path normalizedPath = heapDumpPath.toAbsolutePath().normalize();
        CacheEntry entry = heapCache.get(normalizedPath);
        return entry != null && entry.heapRef.get() != null;
    }

    private void evictIdleEntries() {
        Instant threshold = clock.instant().minus(idleTimeout);

        heapCache.entrySet().removeIf(entry -> {
            CacheEntry cacheEntry = entry.getValue();

            // Check if SoftReference was cleared
            if (cacheEntry.heapRef.get() == null) {
                LOG.debug("Evicting garbage-collected heap entry: path={}", entry.getKey());
                return true;
            }

            // Check if idle timeout exceeded
            if (cacheEntry.lastAccess.isBefore(threshold)) {
                LOG.info("Evicting idle heap entry: path={} lastAccess={}", entry.getKey(), cacheEntry.lastAccess);
                return true;
            }

            return false;
        });
    }

    /**
     * Shutdown the eviction scheduler. Call this when the loader is no longer needed.
     */
    public void shutdown() {
        evictionScheduler.shutdown();
        heapCache.clear();
    }

    private static class CacheEntry {
        final SoftReference<Heap> heapRef;
        volatile Instant lastAccess;

        CacheEntry(Heap heap, Instant lastAccess) {
            this.heapRef = new SoftReference<>(heap);
            this.lastAccess = lastAccess;
        }
    }
}
