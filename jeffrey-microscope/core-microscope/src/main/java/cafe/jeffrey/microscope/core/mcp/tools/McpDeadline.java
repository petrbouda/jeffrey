/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.JeffreyInternalException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * The moment a remote tool must have its answer by: armed from the budget the tool is configured
 * with when the call starts, and read back as what is left of it. Kept on the monotonic clock,
 * never the wall clock, so a clock adjustment neither cuts a scan short nor stretches it.
 */
public final class McpDeadline {

    private static final LongSupplier SYSTEM_TICKER = System::nanoTime;

    private final LongSupplier ticker;
    private final long expiresAtNanos;

    private McpDeadline(LongSupplier ticker, long expiresAtNanos) {
        this.ticker = ticker;
        this.expiresAtNanos = expiresAtNanos;
    }

    /** A deadline that far out from now. */
    public static McpDeadline after(Duration budget) {
        return after(budget, SYSTEM_TICKER);
    }

    /** The same, read from {@code ticker} — a test's clock that never moves. */
    static McpDeadline after(Duration budget, LongSupplier ticker) {
        return new McpDeadline(ticker, ticker.getAsLong() + budget.toNanos());
    }

    /** What is left, in {@code unit}; zero or less once it has elapsed. */
    public long timeRemaining(TimeUnit unit) {
        return unit.convert(expiresAtNanos - ticker.getAsLong(), TimeUnit.NANOSECONDS);
    }

    /**
     * What is left, and no more than {@code cap}: how long a tool may still wait on a transfer once
     * the remote preflight has spent part of the response budget, bounded by what the client is
     * kept waiting (the task wait for a client that follows tasks).
     *
     * @param elapsedDescription what the refusal says when nothing is left, naming whose budget ran out
     * @throws JeffreyInternalException when the deadline has already elapsed, reported as the remote
     *         being unavailable
     */
    public Duration remainingWithin(Duration cap, String elapsedDescription) {
        long remainingNanos = timeRemaining(TimeUnit.NANOSECONDS);
        if (remainingNanos <= 0) {
            throw new JeffreyInternalException(ErrorCode.REMOTE_UNAVAILABLE, elapsedDescription);
        }
        Duration remaining = Duration.ofNanos(remainingNanos);
        return remaining.compareTo(cap) < 0 ? remaining : cap;
    }
}
