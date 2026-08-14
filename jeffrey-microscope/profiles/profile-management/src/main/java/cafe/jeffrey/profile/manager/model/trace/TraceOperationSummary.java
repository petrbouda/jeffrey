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

import java.util.List;

/**
 * The two things an operation's summary cannot work out from the trace list it already holds: what
 * its spans are, and which threads they ran on.
 * <p>
 * Everything else on that page — call count, percentiles, distribution, concurrency — is arithmetic
 * over the traces themselves, and is done where they are already loaded rather than fetched twice.
 *
 * @param spans   span names ranked by total time, inclusive of their children
 * @param threads how the spans divide between platform and virtual threads
 */
public record TraceOperationSummary(List<TraceOperationSpanRow> spans, TraceOperationThreads threads) {
}
