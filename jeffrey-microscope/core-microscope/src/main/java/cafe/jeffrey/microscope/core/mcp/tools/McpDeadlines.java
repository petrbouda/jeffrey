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

import io.grpc.Context;
import io.grpc.Deadline;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The one timer behind every deadline the hub tools arm.
 * <p>
 * A gRPC {@link Deadline} needs somewhere to schedule the shot that cancels its {@link Context}. That
 * timer does nothing but sleep: the task is armed when a remote call starts and cancelled when it
 * answers, which is almost always, and {@code setRemoveOnCancelPolicy} takes the cancelled ones out
 * of the queue rather than letting them accumulate. The scan, the download and the per-file fetch
 * had each grown their own single-thread copy of exactly that, which is three idle threads and
 * three names in a thread dump for one job.
 * <p>
 * One daemon thread is enough for all of them and cannot be the thing that runs out: nothing is
 * executed on it but the cancellation of a context, and a deadline that fires hands the work back to
 * the gRPC channel's own threads.
 */
public final class McpDeadlines {

    private static final ScheduledExecutorService SCHEDULER = scheduler();

    private McpDeadlines() {
    }

    /** A deadline that many nanoseconds out, spelled from the {@link Duration} the tools configure. */
    public static Deadline after(Duration budget) {
        return Deadline.after(budget.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * {@code parent} with {@code deadline} attached. The caller owns the returned context and must
     * cancel it in a {@code finally}, the way {@link Context.CancellableContext} asks.
     */
    public static Context.CancellableContext withDeadline(Context parent, Deadline deadline) {
        return parent.withDeadline(deadline, SCHEDULER);
    }

    /**
     * The same, for work whose deadline is a budget rather than an instant already fixed — a transfer
     * continuing in the background, which is detached from the request that started it and so hangs
     * off {@link Context#ROOT} rather than the caller's context.
     */
    public static Context.CancellableContext withDeadlineAfter(Context parent, Duration budget) {
        return parent.withDeadlineAfter(budget.toNanos(), TimeUnit.NANOSECONDS, SCHEDULER);
    }

    private static ScheduledExecutorService scheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("mcp-hub-deadline-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
