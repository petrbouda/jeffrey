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

package cafe.jeffrey.jfr.events.trace;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The executor decorator behind {@link Tracer#propagating(ExecutorService)}: every task is wrapped
 * at submission time with the span then in progress on the submitting thread, and re-entered on
 * whatever thread the delegate runs it on.
 * <p>
 * Capture happens per <em>submission</em>, not per decorator: the same wrapped pool serves many
 * requests, each task carrying the span of the request that submitted it. A task submitted outside
 * any span is handed to the delegate untouched.
 *
 * @see Tracer#propagating(ExecutorService)
 */
final class ContextPropagatingExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    ContextPropagatingExecutorService(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(wrap(command));
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks));
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(wrapAll(tasks), timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return delegate.invokeAny(wrapAll(tasks));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(wrapAll(tasks), timeout, unit);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    private static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");

        SpanContext context = Tracer.current().orElse(null);
        if (context == null) {
            return task;
        }
        return () -> Tracer.reenter(context, task);
    }

    private static <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");

        SpanContext context = Tracer.current().orElse(null);
        if (context == null) {
            return task;
        }
        return () -> Tracer.reenter(context, task::call);
    }

    private static <T> Collection<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
        return tasks.stream()
                .map(task -> (Callable<T>) wrap(task))
                .toList();
    }
}
