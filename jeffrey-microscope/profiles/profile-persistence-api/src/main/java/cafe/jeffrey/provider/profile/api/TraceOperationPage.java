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

import java.util.List;

/**
 * One page of operations, with how many the filter matched in total — the aggregated counterpart to
 * {@link TracePage}, and for the same reason.
 *
 * @param operations    the rows for this page, already ordered
 * @param totalMatching how many distinct trace types the same filter matches, ignoring limit and
 *                      offset
 */
public record TraceOperationPage(List<TraceOperationRecord> operations, long totalMatching) {

    public static final TraceOperationPage EMPTY = new TraceOperationPage(List.of(), 0);

    public TraceOperationPage {
        operations = List.copyOf(operations);
    }
}
