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
 * One page of traces, with how many the filter matched in total.
 * <p>
 * The total travels with the rows so the list can say whether what it drew is all there was. Without
 * it a capped list is indistinguishable from a complete one, which is the question a reader looking
 * at exactly 100 rows always has.
 *
 * @param traces        the page's rows, already ordered
 * @param totalMatching how many traces match the same filter, ignoring the page bounds
 */
public record TracesPage(List<TraceRow> traces, long totalMatching) {
}
