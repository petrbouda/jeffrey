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

package cafe.jeffrey.microscope.grpc.client;

import cafe.jeffrey.shared.common.activity.ActivityLimits;
import cafe.jeffrey.shared.common.activity.ActivityOrder;

/** One page of one scan's buckets. */
public record ActivityScanQuery(
        ActivityScanTarget target,
        ActivityOrder order,
        int limit,
        int offset) {

    public ActivityScanQuery {
        if (target == null) {
            throw new IllegalArgumentException("A scan target is required");
        }
        if (order == null) {
            throw new IllegalArgumentException("An order is required");
        }
        if (limit < 1 || limit > ActivityLimits.MAX_RESULT_BUCKETS) {
            throw new IllegalArgumentException("limit must be 1–" + ActivityLimits.MAX_RESULT_BUCKETS);
        }
        if (offset < 0 || offset > ActivityLimits.MAX_BUCKETS) {
            throw new IllegalArgumentException("offset must be 0–" + ActivityLimits.MAX_BUCKETS);
        }
    }
}
