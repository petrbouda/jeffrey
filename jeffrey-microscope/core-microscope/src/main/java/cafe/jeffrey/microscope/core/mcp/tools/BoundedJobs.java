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
package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.shared.common.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Runs work that outlasts a tool call, and waits only as long as a client will.
 * <p>
 * Two of Jeffrey's MCP tools do something genuinely long: parsing every event of a recording, and
 * pulling a multi-gigabyte session off a hub. Neither is slow in a way that can be optimised away, and
 * both routinely run past the minute a client waits before abandoning the call. What makes that worse
 * than a slow answer is what the client does next — it retries, and a retry of either one repeats the
 * whole thing, leaving a second import of the same file or a second transfer of the same session.
 * <p>
 * So work is started here and awaited with a budget. Small enough to finish inside it, and the caller
 * gets the answer in one call exactly as before; larger, and the call returns while the client is
 * still listening, saying the work continues. The key is what the caller already holds — a recording
 * id, a session reference — so a second call for the same thing joins the first rather than starting
 * a rival.
 *
 * @param <K> what identifies one piece of work to the caller
 * @param <V> what it produces
 */
public class BoundedJobs<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(BoundedJobs.class);

    /**
     * How long a tool call waits before handing back something to poll.
     * <p>
     * Under the shortest client timeout worth designing for — Codex defaults to sixty seconds — with
     * enough margin that the answer is still travelling when a stricter client gives up.
     */
    public static final Duration WAIT_BUDGET = Duration.ofSeconds(45);

    private final Map<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    private final Duration budget;

    public BoundedJobs() {
        this(WAIT_BUDGET);
    }

    public BoundedJobs(Duration budget) {
        if (budget == null || budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("budget must be positive: budget=" + budget);
        }
        this.budget = budget;
    }

    /**
     * Starts {@code work} unless it is already running for this key, then waits up to the budget.
     *
     * @return the result when it finished in time, empty when it is still running
     */
    public Optional<V> runWithin(K key, Supplier<V> work) {
        boolean[] started = {false};
        CompletableFuture<V> job = inFlight.computeIfAbsent(key, id -> {
            started[0] = true;
            LOG.debug("Starting a bounded MCP job: key={}", id);
            return CompletableFuture.supplyAsync(work, Schedulers.sharedVirtual());
        });

        // Registered outside computeIfAbsent, and only by whoever started the job. Work that finishes
        // before this line runs the callback on this thread, and removing from the map inside the
        // mapping function is a recursive update the map refuses.
        if (started[0]) {
            job.whenComplete((result, error) -> {
                inFlight.remove(key, job);
                if (error != null) {
                    LOG.warn("A bounded MCP job failed: key={} message={}", key, error.getMessage());
                }
            });
        }

        try {
            return Optional.of(job.get(budget.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            // Not a failure: the work carries on and the caller is told how to follow it.
            return Optional.empty();
        } catch (ExecutionException e) {
            throw asRuntime(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for job: " + key, e);
        }
    }

    /**
     * Whether work started here is still running for this key.
     */
    public boolean isRunning(K key) {
        CompletableFuture<V> job = inFlight.get(key);
        return job != null && !job.isDone();
    }

    /**
     * The failure the caller should see, which is the one the work actually threw. Reporting the
     * {@link ExecutionException} instead would give every failure the same wrapper and hide the
     * message that says which file could not be parsed or which hub stopped answering.
     */
    private static RuntimeException asRuntime(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(cause.getMessage(), cause);
    }
}
