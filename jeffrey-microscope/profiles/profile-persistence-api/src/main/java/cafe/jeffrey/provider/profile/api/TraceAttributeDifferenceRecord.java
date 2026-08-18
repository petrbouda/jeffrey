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
 * One value, and how much more common it is inside the selection than outside it.
 *
 * @param key             which key the value belongs to
 * @param value           the value, bucketed to an order of magnitude where the key is numeric and
 *                        wide — the caller renders it verbatim
 * @param selectionTraces traces of the selection carrying it
 * @param baselineTraces  traces outside the selection carrying it
 * @param lift            the selection's share of it over the baseline's share; a baseline share of
 *                        zero is floored at one trace's worth, so a value unique to the selection
 *                        ranks high rather than dividing by nothing
 */
public record TraceAttributeDifferenceRecord(
        TraceAttributeKeyId key,
        String value,
        long selectionTraces,
        long baselineTraces,
        double lift) {
}
