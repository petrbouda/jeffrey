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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * How an operation's spans divide across threads. Only the platform-thread spans can carry a
 * flamegraph: the profiler attributes every sample to the carrier, never to the virtual thread
 * mounted on it.
 *
 * A span whose thread could not be resolved is counted separately rather than as a platform span:
 * not knowing where it ran is not the same as knowing samples are available for it.
 *
 * @param distinctThreads how many threads the operation's spans ran on
 * @param platformSpans   spans on a platform thread
 * @param virtualSpans    spans on a virtual thread
 * @param unknownSpans    spans whose thread could not be resolved
 */
public record TraceOperationThreads(
        long distinctThreads, long platformSpans, long virtualSpans, long unknownSpans) {
}
