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

import java.util.Locale;

/**
 * Durations and shares, formatted the one way both trace exports write them.
 * <p>
 * Shared because the two documents are read by the same kind of reader in the same sitting: a model
 * that has learned "125ms" in a trace bundle should not have to relearn "0.125 s" in an operation
 * one.
 * <p>
 * The unit follows the magnitude, but the milliseconds band is deliberately wide — everything from
 * 1ms to 10s — so that values a reader is likely to compare with each other almost always share a
 * unit. A column that mixes units invites a model to compare the numbers without their suffixes.
 */
final class TraceAiFormat {

    private static final long NANOS_PER_MICRO = 1_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Below a millisecond, milliseconds round to "0ms" and stop saying anything useful. */
    private static final long MICROS_CUTOFF_NANOS = NANOS_PER_MILLI;
    private static final long SECONDS_CUTOFF_NANOS = 10 * NANOS_PER_SECOND;

    private static final long BYTES_PER_KIB = 1024L;
    private static final long BYTES_PER_MIB = 1024L * 1024L;

    private TraceAiFormat() {
    }

    /**
     * A duration with its unit attached, never bare. A bare number in a document a model will do
     * arithmetic on is an invitation to add milliseconds to microseconds.
     */
    static String duration(long nanos) {
        if (nanos < MICROS_CUTOFF_NANOS) {
            return nanos / NANOS_PER_MICRO + "us";
        }
        if (nanos < SECONDS_CUTOFF_NANOS) {
            return nanos / NANOS_PER_MILLI + "ms";
        }
        return String.format(Locale.ROOT, "%.1fs", (double) nanos / NANOS_PER_SECOND);
    }

    /**
     * A count with its noun, pluralised. The document is prose meant to be read, and "1 events" is
     * the kind of seam that makes a generated report read as generated.
     */
    static String count(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    /**
     * A byte count with its unit attached, and the exact figure beside it once the unit rounds.
     * <p>
     * Both, because the two readings serve different questions: {@code 8.0 KiB} is what a reader
     * compares against a buffer size, and {@code 8192} is what one does arithmetic on. Printing only
     * the rounded form is how "mean 4.0 KiB" stops being able to tell 4096 from 4600.
     */
    static String bytes(long bytes) {
        if (bytes < BYTES_PER_KIB) {
            return bytes + " B";
        }
        return scaled(bytes) + " (" + bytes + " B)";
    }

    /**
     * The same figure as a per-operation rate. Written with the unit attached to {@code /op} rather
     * than trailing the exact count, because "1.0 MiB (1048576 B)/op" reads as if the parenthetical
     * were the thing divided.
     */
    static String bytesPerOperation(long bytes) {
        if (bytes < BYTES_PER_KIB) {
            return bytes + " B/op";
        }
        return scaled(bytes) + "/op (" + bytes + " B)";
    }

    private static String scaled(long bytes) {
        return bytes < BYTES_PER_MIB
                ? String.format(Locale.ROOT, "%.1f KiB", (double) bytes / BYTES_PER_KIB)
                : String.format(Locale.ROOT, "%.1f MiB", (double) bytes / BYTES_PER_MIB);
    }

    /** A share of a whole, as a percentage. Returns {@code 0%} rather than dividing by zero. */
    static String percent(long part, long whole) {
        if (whole <= 0) {
            return "0%";
        }
        double share = (double) part / whole * 100.0;
        if (share > 0 && share < 0.1) {
            return "<0.1%";
        }
        return String.format(Locale.ROOT, share < 10 ? "%.1f%%" : "%.0f%%", share);
    }
}
