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

package cafe.jeffrey.provider.profile.api;

/**
 * One stretch during which the whole JVM was stopped, overlapping a trace.
 * <p>
 * Global by nature: a collection pause and a safepoint are recorded on a VM thread and halt every
 * application thread, so they belong to the trace's window rather than to any one of its spans. That
 * is exactly what makes them worth drawing across a waterfall — one band explains a gap in several
 * spans at once, which no per-span number can.
 *
 * @param category      {@link TraceContextCategory#GC_PAUSE} or
 *                      {@link TraceContextCategory#SAFEPOINT}
 * @param label         what the event called itself — the GC phase name, the VM operation — so the
 *                      band can say which pause it was rather than only that there was one
 * @param fromEpochMicros when the pause began, absolute, in the same units a span's start carries
 * @param toEpochMicros   when it ended
 */
public record TracePauseRecord(
        TraceContextCategory category,
        String label,
        long fromEpochMicros,
        long toEpochMicros) {

    /** How long the pause lasted, in nanoseconds, to match every other duration in the API. */
    public long durationNanos() {
        return (toEpochMicros - fromEpochMicros) * 1_000L;
    }
}
