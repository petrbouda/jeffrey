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

import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationSnapshot;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.shared.common.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Keyed background work with bounded waits and attempt-specific cancellation. */
public class BoundedJobs<K, V> {

    private static final Logger LOG = LoggerFactory.getLogger(BoundedJobs.class);
    public static final Duration WAIT_BUDGET = Duration.ofSeconds(45);
    public static final Duration COMPLETED_RETENTION = Duration.ofHours(1);

    private final Map<K, Attempt<V>> jobs = new ConcurrentHashMap<>();
    private final Duration budget;
    private final Duration retention;
    private final Clock clock;
    private final Executor scheduler;

    public BoundedJobs() {
        this(WAIT_BUDGET);
    }

    public BoundedJobs(Duration budget) {
        this(budget, COMPLETED_RETENTION, Clock.systemUTC());
    }

    public BoundedJobs(Duration budget, Duration retention, Clock clock) {
        this(budget, retention, clock, Schedulers.sharedVirtual());
    }

    /**
     * @param scheduler what runs the work. Visible for the tests that need to decide what scheduling
     *                  does -- refuse a job, or run it on the calling thread -- rather than wait on a
     *                  shared executor to behave a particular way.
     */
    BoundedJobs(Duration budget, Duration retention, Clock clock, Executor scheduler) {
        validateBudget(budget);
        validateBudget(retention);
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
        this.clock = clock;
        this.budget = budget;
        this.retention = retention;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public Duration waitBudget() {
        return budget;
    }

    public Optional<V> runWithin(K key, Supplier<V> work) {
        return runWithin(key, budget, work);
    }

    public Optional<V> runWithin(K key, Duration waitBudget, Supplier<V> work) {
        return runWithin(key, waitBudget, true, work);
    }

    public Optional<V> runWithin(K key, Duration waitBudget, boolean retryFailure, Supplier<V> work) {
        return runWithin(key, waitBudget, retryFailure, value -> false, work);
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
        return awaitWithin(startOrJoin(key, retryFailure, reuseSuccess, work), waitBudget);
    }

    public OperationHandle<V> startOrJoin(K key, boolean retryFailure,
            Predicate<V> reuseSuccess, Supplier<V> work) {
        return startOrJoin(key, retryFailure, reuseSuccess, control -> work.get());
    }

    public OperationHandle<V> startOrJoin(K key, boolean retryFailure,
            Predicate<V> reuseSuccess, Function<JobControl, V> work) {
        Objects.requireNonNull(reuseSuccess, "reuseSuccess");
        Objects.requireNonNull(work, "work");
        jobs.values().removeIf(this::expired);
        AtomicBoolean started = new AtomicBoolean();
        Attempt<V> attempt = jobs.compute(key, (id, existing) -> {
            if (existing != null) {
                OperationSnapshot<V> snapshot = existing.snapshot();
                if (!snapshot.state().terminal()
                        || (snapshot.state() != OperationState.COMPLETED && !retryFailure)
                        || (snapshot.state() == OperationState.COMPLETED && reuseSuccess.test(snapshot.result()))) {
                    return existing;
                }
            }
            started.set(true);
            return new Attempt<>(clock);
        });
        if (started.get()) {
            // The future is only a result carrier. Cancellation interrupts the tracked worker.
            try {
                scheduler.execute(() -> attempt.execute(work));
            } catch (RuntimeException e) {
                // Nothing is going to run this attempt, and an attempt that never runs never reaches
                // a terminal state: finishedAt stays null, so the sweep never takes it, isRunning
                // keeps answering yes, and every later call for this key joins work that does not
                // exist. Failing it here is what lets the key be asked for again.
                attempt.failToStart(e);
                throw e;
            }
        }
        return attempt;
    }

    public Optional<V> awaitWithin(OperationHandle<V> handle) {
        return awaitWithin(handle, budget);
    }

    public Optional<V> awaitWithin(OperationHandle<V> handle, Duration waitBudget) {
        validateBudget(waitBudget);
        Attempt<V> attempt = asAttempt(handle);
        try {
            return Optional.of(attempt.result.get(waitBudget.toNanos(), TimeUnit.NANOSECONDS));
        } catch (TimeoutException e) {
            return Optional.empty();
        } catch (ExecutionException e) {
            throw asRuntime(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for operation: "
                    + attempt.operationId, e);
        }
    }

    /** Used by an enclosing import worker which owns the full copy-and-analysis lifecycle. */
    public V awaitCompletion(OperationHandle<V> handle) {
        try {
            return asAttempt(handle).result.get();
        } catch (ExecutionException e) {
            throw asRuntime(e.getCause());
        } catch (InterruptedException e) {
            handle.cancel();
            // The enclosing worker cannot release ownership while the inner worker is still alive.
            try {
                asAttempt(handle).result.handle((value, failure) -> null).join();
            } finally {
                Thread.currentThread().interrupt();
            }
            OperationSnapshot<V> outcome = handle.snapshot();
            if (outcome.state() == OperationState.COMPLETED) {
                return outcome.result();
            }
            throw outcome.failure() == null ? new CancellationException("Analysis cancelled") : outcome.failure();
        }
    }

    /** Records a durable result already present locally, without scheduling replacement work. */
    public OperationHandle<V> rememberCompleted(K key, V value) {
        jobs.values().removeIf(this::expired);
        return jobs.compute(key, (id, existing) -> {
            if (existing != null && !expired(existing)) {
                OperationSnapshot<V> snapshot = existing.snapshot();
                if (!snapshot.state().terminal() || Objects.equals(value, snapshot.result())) {
                    return existing;
                }
            }
            Attempt<V> completed = new Attempt<>(clock);
            synchronized (completed) {
                completed.value = value;
                completed.state = OperationState.COMPLETED;
                completed.phase = "already_available";
                completed.finishedAt = clock.instant();
                completed.result.complete(value);
            }
            return completed;
        });
    }

    public Optional<OperationHandle<V>> current(K key) {
        Attempt<V> attempt = jobs.get(key);
        return attempt == null || expired(attempt) ? Optional.empty() : Optional.of(attempt);
    }

    public boolean isRunning(K key) {
        return current(key).map(handle -> !handle.snapshot().state().terminal()).orElse(false);
    }

    public Optional<Outcome<V>> outcome(K key) {
        return current(key).map(OperationHandle::snapshot).filter(snapshot -> snapshot.state().terminal())
                .map(snapshot -> snapshot.state() == OperationState.COMPLETED
                        ? new Outcome<>(snapshot.result(), null)
                        : new Outcome<>(null, snapshot.failure() == null
                                ? new CancellationException("Cancelled") : snapshot.failure()));
    }

    private boolean expired(Attempt<V> attempt) {
        Instant finished = attempt.finishedAt;
        return finished != null && finished.isBefore(clock.instant().minus(retention));
    }

    private static void validateBudget(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive: " + duration);
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> Attempt<V> asAttempt(OperationHandle<V> handle) {
        if (!(handle instanceof Attempt<?>)) {
            throw new IllegalArgumentException("This operation was not started by BoundedJobs");
        }
        return (Attempt<V>) handle;
    }

    private static RuntimeException asRuntime(Throwable failure) {
        return failure instanceof RuntimeException runtime ? runtime
                : new IllegalStateException(failure == null ? "Operation failed without a cause" : failure.getMessage(), failure);
    }

    public interface JobControl {
        void checkCancellation();
        boolean cancellationRequested();
        void phase(String phase);
        void progress(Object progress);
        void onCancellation(Runnable callback);
    }

    private static final class Attempt<V> implements OperationHandle<V>, JobControl {
        private final String operationId = UUID.randomUUID().toString();
        private final Clock clock;
        private final Instant startedAt;
        private final CompletableFuture<V> result = new CompletableFuture<>();
        private OperationState state = OperationState.QUEUED;
        private volatile Instant finishedAt;
        private boolean cancellationRequested;
        private Thread worker;
        private Runnable cancellationHook;
        private String phase = "queued";
        private volatile Object progress;
        private V value;
        private RuntimeException failure;

        private Attempt(Clock clock) {
            this.clock = clock;
            this.startedAt = clock.instant();
        }

        private void execute(Function<JobControl, V> work) {
            V produced = null;
            RuntimeException error = null;
            try {
                synchronized (this) {
                    worker = Thread.currentThread();
                    checkCancellation();
                    state = OperationState.RUNNING;
                    phase = "running";
                }
                produced = work.apply(this);
                if (produced == null) {
                    throw new IllegalStateException("A bounded MCP job returned no result: " + operationId);
                }
            } catch (Throwable e) {
                error = asRuntime(e);
            } finally {
                // Progress can involve a database read. It must not hold the cancellation monitor,
                // including while the worker freezes its final status before releasing ownership.
                Object finalProgress = resolveProgress(progress);
                synchronized (this) {
                    worker = null;
                    cancellationHook = null;
                    progress = finalProgress;
                    value = error == null ? produced : null;
                    failure = error;
                    state = error == null ? OperationState.COMPLETED
                            : acknowledgedCancellation(error) ? OperationState.CANCELLED : OperationState.FAILED;
                    finishedAt = clock.instant();
                    // Publish after the worker has relinquished ownership. No cancel(true) is used.
                    if (error == null) {
                        result.complete(produced);
                    } else {
                        result.completeExceptionally(error);
                    }
                }
                if (error != null) {
                    LOG.warn("A bounded MCP job failed: operation_id={} message={}", operationId, error.getMessage());
                }
                Thread.interrupted();
            }
        }

        /**
         * Terminates an attempt whose work was never scheduled, along the same path its own
         * {@code finally} would have taken.
         */
        private void failToStart(RuntimeException error) {
            synchronized (this) {
                if (state.terminal()) {
                    return;
                }
                worker = null;
                cancellationHook = null;
                value = null;
                failure = error;
                state = OperationState.FAILED;
                phase = "not_started";
                finishedAt = clock.instant();
                result.completeExceptionally(error);
            }
        }

        @Override
        public String operationId() {
            return operationId;
        }

        @Override
        public Instant startedAt() {
            return startedAt;
        }

        @Override
        public Instant finishedAt() {
            return finishedAt;
        }

        private static boolean acknowledgedCancellation(Throwable error) {
            Throwable cause = error;
            for (int depth = 0; cause != null && depth < 20; depth++) {
                if (cause instanceof CancellationException || cause instanceof InterruptedException
                        || cause instanceof InterruptedIOException || cause instanceof ClosedByInterruptException) {
                    return true;
                }
                cause = cause.getCause();
            }
            return false;
        }

        @Override
        public OperationSnapshot<V> snapshot() {
            Object source = progress;
            Object details = resolveProgress(source);
            synchronized (this) {
                // A slow observation must not overwrite the final progress frozen by the worker.
                return snapshotWithProgress(state.terminal() ? progress : details);
            }
        }

        @Override
        public synchronized OperationSnapshot<V> lifecycleSnapshot() {
            // Cancellation reports current ownership immediately; it never waits for a progress query.
            return snapshotWithProgress(progress instanceof Supplier<?> ? null : progress);
        }

        private OperationSnapshot<V> snapshotWithProgress(Object details) {
            return new OperationSnapshot<>(operationId, state, startedAt, finishedAt,
                    cancellationRequested, phase, details, value, failure);
        }

        private static Object resolveProgress(Object source) {
            try {
                return source instanceof Supplier<?> supplier ? supplier.get() : source;
            } catch (RuntimeException e) {
                // Reporting progress must never prevent terminal publication or release of ownership.
                return Map.of("unavailable", "Progress could not be read");
            }
        }

        @Override
        public boolean cancel() {
            Runnable hook;
            synchronized (this) {
                if (state.terminal() || cancellationRequested) {
                    return false;
                }
                cancellationRequested = true;
                state = OperationState.CANCEL_REQUESTED;
                hook = cancellationHook;
                if (worker != null) {
                    worker.interrupt();
                }
            }
            invokeHook(hook);
            return true;
        }

        @Override
        public synchronized void checkCancellation() {
            if (cancellationRequested) {
                throw new CancellationException("Cancelled");
            }
        }

        @Override
        public synchronized void phase(String nextPhase) {
            phase = nextPhase;
        }

        @Override
        public synchronized boolean cancellationRequested() {
            return cancellationRequested;
        }

        @Override
        public synchronized void progress(Object nextProgress) {
            progress = nextProgress;
        }

        @Override
        public void onCancellation(Runnable callback) {
            boolean invoke;
            synchronized (this) {
                cancellationHook = callback;
                invoke = cancellationRequested;
            }
            if (invoke) {
                invokeHook(callback);
            }
        }

        private static void invokeHook(Runnable callback) {
            if (callback != null) {
                try {
                    callback.run();
                } catch (RuntimeException e) {
                    LOG.warn("Operation cancellation hook failed: message={}", e.getMessage());
                }
            }
        }
    }

    /** Exactly one of value and failure is present. */
    public record Outcome<V>(V value, RuntimeException failure) {
        public Outcome {
            if ((value == null) == (failure == null)) {
                throw new IllegalArgumentException("An outcome must contain either a value or a failure");
            }
        }
    }
}
