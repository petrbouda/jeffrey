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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps one {@link McpProfileContext} per profile an MCP client is working on, and lets it go once the
 * client has stopped asking.
 * <p>
 * Concurrent by construction: a client issues several tool calls at once, and two of them landing on the
 * same new profile must acquire one lease between them rather than one each.
 * <p>
 * Eviction is driven by the injected {@link Clock}, so a test can advance time and call
 * {@link #evictIdle} directly; the background sweep that normally calls it is opt-out for exactly that
 * reason. Same shape as {@code HeapDumpSessionCache}, which solves the same problem for heap dumps.
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

    private final McpProfileResolver profileResolver;
    private final DatabaseManagerResolver databaseManagerResolver;
    private final Clock clock;
    private final Duration idleTimeout;

    private final Map<String, McpProfileContext> contexts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService evictor;

    public McpProfileContextCache(
            McpProfileResolver profileResolver,
            DatabaseManagerResolver databaseManagerResolver,
            Clock clock) {
        this(profileResolver, databaseManagerResolver, clock, DEFAULT_IDLE_TIMEOUT, true);
    }

    /**
     * @param sweeping whether to run the background eviction sweep; a test drives {@link #evictIdle}
     *                 itself and would otherwise race with it
     */
    public McpProfileContextCache(
            McpProfileResolver profileResolver,
            DatabaseManagerResolver databaseManagerResolver,
            Clock clock,
            Duration idleTimeout,
            boolean sweeping) {
        if (idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive: idleTimeout=" + idleTimeout);
        }
        this.profileResolver = profileResolver;
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
    McpProfileContext context(String profileId) {
        Instant now = clock.instant();
        McpProfileContext context = contexts.computeIfAbsent(profileId, id -> {
            ProfileManager profileManager = profileResolver.resolve(id);
            LOG.debug("Opening MCP profile context: profile_id={}", id);
            return new McpProfileContext(
                    profileManager, databaseManagerResolver.acquire(profileManager.info()), now);
        });
        context.touch(now);
        return context;
    }

    /**
     * The {@link ProfileManager} of one profile, with its pool pinned for the session.
     */
    public ProfileManager profileManager(String profileId) {
        return context(profileId).profileManager();
    }

    /**
     * Drops a profile whose context can no longer be trusted — it was deleted, or its pool was closed
     * under us. The next call re-resolves it, which is the honest answer either way.
     */
    public void evict(String profileId) {
        McpProfileContext removed = contexts.remove(profileId);
        if (removed != null) {
            LOG.debug("Evicting MCP profile context: profile_id={}", profileId);
            removed.close();
        }
    }

    /**
     * Releases every context untouched for longer than the idle timeout.
     */
    public void evictIdle() {
        Instant threshold = clock.instant().minus(idleTimeout);
        List<String> stale = contexts.entrySet().stream()
                .filter(entry -> entry.getValue().idleSince(threshold))
                .map(Map.Entry::getKey)
                .toList();
        for (String profileId : stale) {
            evict(profileId);
        }
    }

    /**
     * @return how many profiles are currently pinned; for tests and the status endpoint
     */
    public int size() {
        return contexts.size();
    }

    @Override
    public void close() {
        if (evictor != null) {
            evictor.shutdownNow();
        }
        List<String> all = List.copyOf(contexts.keySet());
        for (String profileId : all) {
            evict(profileId);
        }
    }
}
