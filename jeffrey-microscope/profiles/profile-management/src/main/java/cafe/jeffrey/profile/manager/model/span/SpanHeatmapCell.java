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

package cafe.jeffrey.profile.manager.model.span;

/**
 * One cell of the spans heatmap: a (tag, time-bucket) intersection.
 *
 * @param bucket    zero-based time bucket index
 * @param count     number of spans of this tag starting in this bucket
 * @param p95Nanos  p95 duration of those spans (0 when {@code count == 0})
 */
public record SpanHeatmapCell(
        int bucket,
        long count,
        long p95Nanos) {
}
