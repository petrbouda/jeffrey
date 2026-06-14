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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import java.util.List;

/**
 * Promotion/tenuring insight for the GC deep-tuning tab.
 *
 * @param gcs per-collection survivor-age distributions, most recent collection first
 */
public record TenuringData(List<TenuringGcSummary> gcs) {

    /**
     * Survivor-age distribution of one garbage collection, from {@code jdk.TenuringDistribution}.
     *
     * @param gcId           collection identifier
     * @param totalSizeBytes total surviving bytes across all ages
     * @param buckets        per-age sizes, ascending by age
     */
    public record TenuringGcSummary(long gcId, long totalSizeBytes, List<TenuringAgeBucket> buckets) {
    }

    /**
     * Surviving bytes of one tenuring age within a single collection.
     */
    public record TenuringAgeBucket(int age, long sizeBytes) {
    }
}
