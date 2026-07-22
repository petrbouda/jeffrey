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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.heapdump.model.IndexBuildProgressListener;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Process-wide cache of open {@link HeapDumpSession}s, keyed by the analyzable
 * {@code .hprof} path. Opening a session is expensive — it mmaps the dump and
 * opens the DuckDB index (losing DuckDB's warm buffer pool on every close) —
 * so instead of the previous open-per-request pattern, the session opened for
 * the first request is kept and reused by subsequent requests for the same
 * dump.
 *
 * <p>A {@link HeapDumpSession} wraps a single DuckDB connection and is not
 * safe for concurrent use, so work units against the same dump are serialized
 * on a per-dump lock. Different dumps proceed concurrently.
 *
 * <p>Freshness: a cached session is transparently replaced when the dump's
 * mtime changes or its index sidecar disappears. Lifecycle events that make a
 * session invalid by construction (re-upload, cache deletion, dump deletion)
 * additionally call {@link #invalidate(Path)} so file handles are released
 * before the underlying files are removed. Idle sessions are closed by a
 * background sweep after {@link #DEFAULT_IDLE_TIMEOUT}; the sweep uses the
 * injected {@link Clock}, so tests drive eviction by calling
 * {@link #evictIdle()} directly with a controlled clock.
 */
public final class HeapDumpSessionCache implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpSessionCache.class);

    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(10);

    private static final Duration EVICTION_SWEEP_PERIOD = Duration.ofMinutes(1);

    private static final String EVICTOR_THREAD_NAME = "heap-dump-session-evictor";

    @FunctionalInterface
    public interface SessionWork<R> {
        R apply(HeapDumpSession session) throws SQLException, IOException;
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private HeapDumpSession session;
        private long hprofMtime;
        private volatile Instant lastUsed;
    }

    private final Clock clock;

    private final Duration idleTimeout;

    private final ConcurrentMap<Path, Entry> entries = new ConcurrentHashMap<>();

    private final ScheduledExecutorService evictor;

    public HeapDumpSessionCache(Clock clock) {
        this(clock, DEFAULT_IDLE_TIMEOUT);
    }

    public HeapDumpSessionCache(Clock clock, Duration idleTimeout) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive: idleTimeout=" + idleTimeout);
        }
        this.clock = clock;
        this.idleTimeout = idleTimeout;
        this.evictor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, EVICTOR_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        this.evictor.scheduleAtFixedRate(
                this::evictIdle,
                EVICTION_SWEEP_PERIOD.toMillis(),
                EVICTION_SWEEP_PERIOD.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Runs {@code work} against the cached session for {@code hprofPath},
     * opening (and building the index of) the session first if it is absent or
     * stale. Work units for the same dump are serialized; the session stays
     * open afterwards for the next caller.
     */
    public <R> R withSession(Path hprofPath, SessionWork<R> work) throws IOException, SQLException {
        return withSession(hprofPath, IndexBuildProgressListener.NOOP, work);
    }

    /**
     * Same as {@link #withSession(Path, SessionWork)} but forwards {@code listener}
     * to the index build so callers can observe sub-phase progress. The listener
     * fires only when this call actually builds the index (cold session).
     */
    public <R> R withSession(Path hprofPath, IndexBuildProgressListener listener, SessionWork<R> work)
            throws IOException, SQLException {
        Path key = cacheKey(hprofPath);
        while (true) {
            Entry entry = entries.computeIfAbsent(key, k -> new Entry());
            entry.lock.lock();
            try {
                if (entries.get(key) != entry) {
                    // Evicted/invalidated between lookup and lock — retry with a fresh entry.
                    continue;
                }
                ensureOpen(entry, key, listener);
                entry.lastUsed = clock.instant();
                return work.apply(entry.session);
            } finally {
                entry.lock.unlock();
            }
        }
    }

    /**
     * Closes and discards the cached session for {@code hprofPath}, waiting
     * for any in-flight work unit on it to finish first. Callers must
     * invalidate before deleting the dump or its index sidecar so no open
     * file handles outlive the files.
     */
    public void invalidate(Path hprofPath) {
        Entry entry = entries.remove(cacheKey(hprofPath));
        if (entry == null) {
            return;
        }
        entry.lock.lock();
        try {
            closeSession(entry);
        } finally {
            entry.lock.unlock();
        }
    }

    /**
     * Closes every cached session whose last use is older than the idle
     * timeout. Entries currently running a work unit are skipped. Invoked
     * periodically by the background sweep; public so tests can drive
     * eviction deterministically with a controlled clock.
     */
    public void evictIdle() {
        Instant cutoff = clock.instant().minus(idleTimeout);
        for (Map.Entry<Path, Entry> mapEntry : entries.entrySet()) {
            Entry entry = mapEntry.getValue();
            if (!entry.lock.tryLock()) {
                continue;
            }
            try {
                if (entry.session != null && entry.lastUsed != null && entry.lastUsed.isBefore(cutoff)) {
                    LOG.debug("Evicting idle heap dump session: path={} last_used={}",
                            mapEntry.getKey(), entry.lastUsed);
                    closeSession(entry);
                }
                if (entry.session == null) {
                    entries.remove(mapEntry.getKey(), entry);
                }
            } finally {
                entry.lock.unlock();
            }
        }
    }

    @Override
    public void close() {
        evictor.shutdownNow();
        for (Path key : entries.keySet()) {
            invalidate(key);
        }
    }

    private void ensureOpen(Entry entry, Path hprofPath, IndexBuildProgressListener listener)
            throws IOException, SQLException {
        long hprofMtime = Files.getLastModifiedTime(hprofPath).toMillis();
        if (entry.session != null) {
            boolean indexPresent = Files.exists(HeapDumpIndexPaths.indexFor(hprofPath));
            if (entry.hprofMtime == hprofMtime && indexPresent) {
                return;
            }
            LOG.debug("Cached heap dump session is stale, reopening: path={}", hprofPath);
            closeSession(entry);
        }
        entry.session = HeapDumpSession.openOrBuild(hprofPath, clock, listener);
        entry.hprofMtime = hprofMtime;
    }

    private static void closeSession(Entry entry) {
        if (entry.session == null) {
            return;
        }
        try {
            entry.session.close();
        } catch (RuntimeException e) {
            LOG.warn("Failed to close cached heap dump session: error={}", e.getMessage());
        }
        entry.session = null;
    }

    private static Path cacheKey(Path hprofPath) {
        return hprofPath.toAbsolutePath().normalize();
    }
}
