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

package cafe.jeffrey.shared.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Applies settings changes to the listeners that care about them.
 * <p>
 * Dispatch is <em>asynchronous</em>: rebuilding an AI backend can probe an external CLI for seconds,
 * which must not block the HTTP thread that saved the setting.
 * <p>
 * Dispatch is <em>debounced</em>: the settings UI saves a whole tab at once, so a single user action
 * arrives as several writes in quick succession. Without debouncing, listeners would rebuild once per
 * write and the early rebuilds would observe half-applied state — a new provider paired with a stale
 * base URL, for instance. The debounced run always observes the final state of the store.
 * <p>
 * Dispatch is <em>single-threaded</em>: listeners are never invoked concurrently or re-entrantly. Each
 * one runs inside its own error boundary, so a listener that fails — an AI backend rejecting a bad API
 * key — cannot stop the others from applying.
 */
public final class SettingsChangeDispatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsChangeDispatcher.class);

    private static final Duration DEBOUNCE = Duration.ofMillis(250);
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
    private static final String THREAD_NAME = "jeffrey-settings-dispatch";

    private final SettingsStore store;
    private final Supplier<List<SettingsChangeListener>> listeners;
    private final ScheduledExecutorService executor;
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final AtomicReference<ScheduledFuture<?>> scheduled = new AtomicReference<>();

    /**
     * @param listeners supplies the listeners at apply time rather than at construction. A listener may
     *                  legitimately need the dispatcher itself — to schedule cleanup of something it
     *                  replaced — and resolving the list eagerly would make that a construction cycle.
     */
    public SettingsChangeDispatcher(SettingsStore store, Supplier<List<SettingsChangeListener>> listeners) {
        this.store = store;
        this.listeners = listeners;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Records that the given settings changed and schedules a debounced apply.
     *
     * @param names the setting names whose values were just written to the store
     */
    public void changed(Set<String> names) {
        if (names.isEmpty()) {
            return;
        }

        pending.addAll(names);

        ScheduledFuture<?> previous = scheduled.getAndSet(
                executor.schedule(this::fire, DEBOUNCE.toMillis(), TimeUnit.MILLISECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void fire() {
        Set<String> changed = Set.copyOf(pending);
        pending.removeAll(changed);
        if (changed.isEmpty()) {
            return;
        }

        for (SettingsChangeListener listener : listeners.get()) {
            if (Collections.disjoint(listener.observedSettings(), changed)) {
                continue;
            }

            try {
                listener.onChanged(store);
            } catch (RuntimeException e) {
                LOG.error("Settings change listener failed: listener={} message={}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }

        LOG.info("Applied settings change: keys={}", changed);
    }

    /**
     * Schedules deferred cleanup of a resource that a listener has just replaced.
     * <p>
     * A replaced resource cannot be closed immediately: callers that already resolved it are still
     * using it. Closing it after the longest possible in-flight call is the simplest correct bound.
     *
     * @param cleanup the cleanup action
     * @param delay   how long to wait before running it
     */
    public void scheduleCleanup(Runnable cleanup, Duration delay) {
        executor.schedule(() -> {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                LOG.warn("Deferred settings cleanup failed: message={}", e.getMessage(), e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
