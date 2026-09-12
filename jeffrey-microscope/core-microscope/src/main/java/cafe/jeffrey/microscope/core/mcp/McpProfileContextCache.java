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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps one {@link McpProfileContext} per profile an MCP client is working on, and lets it go once the
 * client has stopped asking.
 * <p>
 * Concurrent by construction: a client issues several tool calls at once, and two of them landing on the
 * same new profile must share one cached database lease while each holds its own call lease.
 * <p>
 * Eviction is driven by the injected {@link Clock}, so a test can advance time and call
 * {@link #evictIdle} directly; the background sweep that normally calls it is opt-out for exactly that
 * reason. Same shape as {@code HeapDumpSessionCache}, which solves the same problem for heap dumps.
 * <p>
 * Every tool call acquires a {@link Lease}. Idle eviction can remove an inactive context, but explicit
 * invalidation or cache shutdown only retires one that is still in use; its database lease closes when
 * the last call releases it. Release also renews the idle window, so a long call gets a full quiet period
 * after it finishes.
 */
public final class McpProfileContextCache implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(McpProfileContextCache.class);

    /**
     * How long a profile stays pinned after the last tool call. Comfortably longer than a reader's
     * pause between questions, short enough that a finished session does not hold a pool overnight.
     */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private static final Duration EVICTION_SWEEP_PERIOD = Duration.ofMinutes(1);
    private static final String EVICTOR_THREAD_NAME = "mcp-profile-context-evictor";

    private final ProfileManagerResolver profileManagerResolver;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final Clock clock;
    private final Duration idleTimeout;

    private final Map<String, McpProfileContext> contexts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public McpProfileContextCache(
            ProfileManagerResolver profileManagerResolver,
            DatabaseManagerResolver databaseManagerResolver,
            Clock clock) {
        this(profileManagerResolver, databaseManagerResolver, clock, DEFAULT_IDLE_TIMEOUT, true);
    }

    /**
     * @param sweeping whether to run the background eviction sweep; a test drives {@link #evictIdle}
     *                 itself and would otherwise race with it
     */
    public McpProfileContextCache(
            ProfileManagerResolver profileManagerResolver,
            DatabaseManagerResolver databaseManagerResolver,
            Clock clock,
            Duration idleTimeout,
            boolean sweeping) {
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive: idleTimeout=" + idleTimeout);
        }
        this.profileManagerResolver = profileManagerResolver;
        this.databaseManagerResolver = databaseManagerResolver;
        this.clock = clock;
        this.idleTimeout = idleTimeout;
        this.evictor = sweeping ? startEvictor() : null;
    }

    private ScheduledExecutorService startEvictor() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, EVICTOR_THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(
                this::evictIdle,
                EVICTION_SWEEP_PERIOD.toMillis(),
                EVICTION_SWEEP_PERIOD.toMillis(),
                TimeUnit.MILLISECONDS);
        return executor;
    }

    /**
     * The context for one profile, opening it on first use.
     *
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when no such profile exists
     */
    public Lease acquire(String profileId) {
        Instant now = clock.instant();
        AtomicReference<McpProfileContext> acquired = new AtomicReference<>();
        contexts.compute(profileId, (id, existing) -> {
            if (closed.get()) {
                throw new IllegalStateException("MCP profile context cache is closed");
            }
            McpProfileContext context = existing;
            if (context == null) {
                ProfileManager profileManager = profileManagerResolver.resolve(id);
                LOG.debug("Opening MCP profile context: profile_id={}", id);
                context = new McpProfileContext(
                        profileManager, databaseManagerResolver.acquire(profileManager.info()), now);
            }
            context.acquire(now);
            acquired.set(context);
            return context;
        });
        McpProfileContext context = acquired.get();
        Lease lease = new Lease(context, clock);
        if (closed.get()) {
            contexts.computeIfPresent(profileId, (id, current) -> {
                if (current != context) {
                    return current;
                }
                current.retire();
                return null;
            });
            lease.close();
            throw new IllegalStateException("MCP profile context cache is closed");
        }
        return lease;
    }

    /**
     * Drops a profile whose context can no longer be trusted — it was deleted, or its pool was closed
     * under us. The next call re-resolves it, which is the honest answer either way.
     */
    public void invalidate(String profileId) {
        contexts.computeIfPresent(profileId, (id, context) -> {
            LOG.debug("Evicting MCP profile context: profile_id={}", profileId);
            context.retire();
            return null;
        });
    }

    /**
     * Releases every context untouched for longer than the idle timeout.
     */
    public void evictIdle() {
        Instant threshold = clock.instant().minus(idleTimeout);
        for (String profileId : List.copyOf(contexts.keySet())) {
            contexts.computeIfPresent(profileId, (id, context) -> {
                if (!context.retireIfIdle(threshold)) {
                    return context;
                }
                LOG.debug("Evicting idle MCP profile context: profile_id={}", id);
                return null;
            });
        }
    }

    /**
     * @return how many profiles are currently pinned; for tests
     */
    public int size() {
        return contexts.size();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (evictor != null) {
            evictor.shutdownNow();
        }
        List<String> all = List.copyOf(contexts.keySet());
        for (String profileId : all) {
            invalidate(profileId);
        }
    }

    /**
     * One active use of a cached profile. Closing it is idempotent because failure paths and cleanup
     * layers may both try to release the same call.
     */
    public static final class Lease implements AutoCloseable {

        private final McpProfileContext context;
        private final Clock clock;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(McpProfileContext context, Clock clock) {
            this.context = context;
            this.clock = clock;
        }

        public ProfileManager profileManager() {
            return context.profileManager();
        }

        public DataSource dataSource() {
            return context.dataSource();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                context.release(clock.instant());
            }
        }
    }
}
