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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.Predicate;

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

    /**
     * How long a terminal outcome stays readable after the work ended.
     * <p>
     * A retained outcome is what lets a client that gave up on the call read the failure -- or the
     * finished result -- on its next poll, so it has to outlive a client's polling rhythm by a wide
     * margin. It must not outlive the process, though: the key is a recording id or a hub session
     * reference, so a map that only ever grows is one entry per import and per download for as long
     * as Jeffrey runs. An hour is the same bound {@code PipelineRunRegistry} puts on a finished run,
     * and for the same reason.
     */
    public static final Duration COMPLETED_RETENTION = Duration.ofHours(1);

    private final Map<K, JobState<V>> jobs = new ConcurrentHashMap<>();
    private final Duration budget;
    private final Duration retention;
    private final Clock clock;

    public BoundedJobs() {
        this(WAIT_BUDGET);
    }

    public BoundedJobs(Duration budget) {
        this(budget, COMPLETED_RETENTION, Clock.systemUTC());
    }

    /**
     * @param retention how long a finished outcome stays readable before it is swept
     * @param clock     what dates an outcome, so a test can age one without waiting
     */
    public BoundedJobs(Duration budget, Duration retention, Clock clock) {
        validateBudget(budget);
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("retention must be positive: retention=" + retention);
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        this.budget = budget;
        this.retention = retention;
        this.clock = clock;
    }

    /**
     * Starts {@code work} unless it is already running for this key, then waits up to the budget.
     *
     * @return the result when it finished in time, empty when it is still running
     */
    public Optional<V> runWithin(K key, Supplier<V> work) {
        return runWithin(key, budget, work);
    }

    /**
     * The same keyed job with a budget chosen by this call. A caller that has already spent part of
     * its response deadline uses this overload to wait only for what remains.
     */
    public Optional<V> runWithin(K key, Duration waitBudget, Supplier<V> work) {
        return runWithin(key, waitBudget, true, work);
    }

    /**
     * Runs or joins a keyed job while deciding atomically whether a retained failure is an explicit
     * retry. This closes the gap between a caller inspecting {@link #outcome(Object)} and starting:
     * the active attempt may fail in between, and {@code false} must still report that failure rather
     * than silently starting its supplier again.
     */
    public Optional<V> runWithin(
            K key, Duration waitBudget, boolean retryFailure, Supplier<V> work) {
        return runWithin(key, waitBudget, retryFailure, _ -> false, work);
    }

    /**
     * Selects a still-valid completed success atomically with joining or starting work. Downloads use
     * this when a transfer may finish while another caller is still validating the remote session;
     * their predicate checks that the local recording still exists. Other callers keep the original
     * restart-after-success policy through the overloads above.
     * <p>
     * {@code reuseSuccess} runs inside the map's own update, holding the bin lock -- which is the point,
     * since deciding outside it is the race this overload exists to close. It must therefore be short
     * and must not reach back into this instance: a local lookup is what it is for, and a remote call
     * or anything that blocks belongs before the call, not in the predicate.
     */
    public Optional<V> runWithin(
            K key, Duration waitBudget, boolean retryFailure, Predicate<V> reuseSuccess, Supplier<V> work) {
        validateBudget(waitBudget);
        Objects.requireNonNull(reuseSuccess, "reuseSuccess");
        // Swept here because this is the only method that adds a key. The map is then bounded by the
        // work actually asked for within the retention window rather than by everything ever asked for.
        evictExpired();

        AtomicBoolean started = new AtomicBoolean();
        JobState<V> selected = jobs.compute(key, (id, existing) -> {
            if (existing instanceof Active<?>) {
                return existing;
            }
            if (existing instanceof Finished<V> finished) {
                Outcome<V> outcome = finished.outcome();
                if (outcome.failure() != null && !retryFailure) {
                    return existing;
                }
                if (outcome.failure() == null && reuseSuccess.test(outcome.value())) {
                    return existing;
                }
            }
            started.set(true);
            LOG.debug("Starting a bounded MCP job: key={}", id);
            return new Active<>(CompletableFuture.supplyAsync(work, Schedulers.sharedVirtual()));
        });
        if (selected instanceof Finished<V> finished) {
            RuntimeException failure = finished.outcome().failure();
            if (failure != null) {
                throw failure;
            }
            return Optional.of(finished.outcome().value());
        }
        Active<V> active = asActive(selected);

        // Registered outside compute(), and only by whoever started this attempt. A future may have
        // finished before this line; whenComplete then runs synchronously and still replaces this
        // exact Active value. replace() prevents an older completion from overwriting a retry.
        if (started.get()) {
            active.future().whenComplete((result, error) -> finish(key, active, result, error));
        }

        try {
            V result = active.future().get(waitBudget.toMillis(), TimeUnit.MILLISECONDS);
            finish(key, active, result, null);
            if (result == null) {
                // Empty already means "still running", so a null result cannot be reported as one.
                // Nothing here supplies null today; saying so is what keeps a future one from being
                // read as a job that never finished.
                throw new IllegalStateException("A bounded MCP job returned no result: key=" + key);
            }
            return Optional.of(result);
        } catch (TimeoutException e) {
            // Not a failure: the work carries on and the caller is told how to follow it.
            return Optional.empty();
        } catch (ExecutionException e) {
            finish(key, active, null, e.getCause());
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
        return jobs.get(key) instanceof Active<?>;
    }

    /**
     * The last terminal outcome for this key. Reading it does not consume it, so status polling can
     * repeat the same late failure. A new explicit {@link #runWithin} call atomically replaces it with
     * an active attempt before starting new work.
     * <p>
     * It is not kept forever: an outcome older than {@link #COMPLETED_RETENTION} is swept, and this
     * then answers empty as it does for work never asked for. Nothing polls for an hour.
     */
    public Optional<Outcome<V>> outcome(K key) {
        JobState<V> state = jobs.get(key);
        // Judged by age rather than by whether the sweep has run yet, so how long an outcome is
        // reported does not depend on whether unrelated work happened to be asked for meanwhile.
        if (state instanceof Finished<V> finished && !expired(finished, clock.instant())) {
            return Optional.of(finished.outcome());
        }
        return Optional.empty();
    }

    private static void validateBudget(Duration budget) {
        if (budget == null || budget.isNegative() || budget.isZero()) {
            throw new IllegalArgumentException("budget must be positive: budget=" + budget);
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> Active<V> asActive(JobState<V> state) {
        return (Active<V>) state;
    }

    private static <K, V> Outcome<V> outcomeOf(K key, V result, Throwable error) {
        if (error != null) {
            return new Outcome<>(null, asRuntime(unwrap(error)));
        }
        if (result == null) {
            return new Outcome<>(null,
                    new IllegalStateException("A bounded MCP job returned no result: key=" + key));
        }
        return new Outcome<>(result, null);
    }

    private void finish(K key, Active<V> active, V result, Throwable error) {
        Outcome<V> outcome = outcomeOf(key, result, error);
        boolean published = jobs.replace(key, active, new Finished<>(outcome, clock.instant()));
        if (published && outcome.failure() != null) {
            LOG.warn("A bounded MCP job failed: key={} message={}",
                    key, outcome.failure().getMessage());
        }
    }

    /**
     * Drops outcomes nothing is going to read. An active attempt is never swept, however long it runs:
     * what bounds one of those is its own work, and dropping it here would start a rival.
     */
    private void evictExpired() {
        Instant now = clock.instant();
        jobs.values().removeIf(state ->
                state instanceof Finished<V> finished && expired(finished, now));
    }

    private boolean expired(Finished<V> finished, Instant now) {
        return finished.finishedAt().isBefore(now.minus(retention));
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
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
        if (cause == null) {
            // ExecutionException does not promise a cause. Without this the failure that reaches the
            // model is a NullPointerException from this line, which says nothing about the job.
            return new IllegalStateException("A bounded MCP job failed without reporting a cause");
        }
        return new IllegalStateException(cause.getMessage(), cause);
    }

    private sealed interface JobState<V> permits Active, Finished {
    }

    private record Active<V>(CompletableFuture<V> future) implements JobState<V> {
    }

    private record Finished<V>(Outcome<V> outcome, Instant finishedAt) implements JobState<V> {
    }

    /** Exactly one of {@code value} and {@code failure} is present. */
    public record Outcome<V>(V value, RuntimeException failure) {

        public Outcome {
            if ((value == null) == (failure == null)) {
                throw new IllegalArgumentException("An outcome must contain either a value or a failure");
            }
        }
    }
}
