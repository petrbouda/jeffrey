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

import java.util.List;

/**
 * The spans heatmap: tags (rows) by time buckets (columns).
 *
 * @param bucketCount number of time-bucket columns
 * @param bucketMillis width of each bucket in milliseconds
 * @param rows        one row per tag
 */
public record SpanHeatmap(
        int bucketCount,
        long bucketMillis,
        List<SpanHeatmapRow> rows) {
}
