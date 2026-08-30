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

package cafe.jeffrey.profile.ai.trace;

/**
 * Every operation of one kind against one target — a file path, or a {@code host:port} — collapsed
 * into the shape they made together.
 * <p>
 * The shape is the point. One 8 KiB read and one 8 KiB read are the same row as sixteen 512-byte
 * reads only if you look at the total; the mean is what tells the two apart, and telling them apart
 * is the whole reason a trace export bothers to carry I/O at all.
 *
 * @param direction  which of the promoted I/O event types these were
 * @param target     the path or peer they were against
 * @param operations how many of them the recording captured — a lower bound, since JFR drops
 *                   operations faster than its I/O threshold
 * @param bytes      how much data moved in total; always {@code 0} for a direction that moves none
 * @param totalNanos the wall-clock those operations cost, summed
 * @param maxNanos   the slowest single one, which is what separates a steady drip from one stall
 */
record TraceIoTarget(
        TraceIoDirection direction,
        String target,
        long operations,
        long bytes,
        long totalNanos,
        long maxNanos) {

    /**
     * The count above which a small mean stops being an anecdote. Below it, a handful of small reads
     * is just as likely to be a header being parsed as a missing buffer.
     */
    private static final long MIN_OPERATIONS_FOR_SHAPE = 8;

    /**
     * The mean below which a run of operations looks unbuffered. Half of {@code
     * BufferedInputStream}'s 8 KiB default: a stream carrying a real buffer lands at or above that
     * default far more often than halfway under it.
     */
    private static final long SMALL_OPERATION_BYTES = 4096;

    /** Bytes per operation — the buffering figure. */
    long meanBytes() {
        return operations > 0 ? bytes / operations : 0L;
    }

    /** Wall-clock per operation, which is the figure that matters for a latency-bound socket. */
    long meanNanos() {
        return operations > 0 ? totalNanos / operations : 0L;
    }

    boolean carriesBytes() {
        return direction.carriesBytes();
    }

    /**
     * Whether this row has the fingerprint of a missing or undersized buffer: enough operations for
     * the mean to mean something, and a mean well under a default buffer's worth.
     * <p>
     * A shape, not a verdict — a protocol whose messages really are 200 bytes long trips it too,
     * which is why the document says so where it prints the marker.
     */
    boolean smallOperations() {
        return carriesBytes()
                && operations >= MIN_OPERATIONS_FOR_SHAPE
                && bytes > 0
                && meanBytes() < SMALL_OPERATION_BYTES;
    }
}
