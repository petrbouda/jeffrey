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

import cafe.jeffrey.shared.common.activity.ActivityOrder;
import cafe.jeffrey.shared.common.activity.ActivityState;

import java.util.List;

/** One observation of a Hub scan, with the gRPC message already left behind. */
public record ActivityScanSnapshot(
        String scanId,
        String workspaceId,
        String projectId,
        String sessionId,
        ActivityState status,
        long startedAt,
        Long finishedAt,
        boolean complete,
        boolean coverageKnown,
        long sourceErrors,
        int filesTotal,
        String error,
        long startTime,
        long endTime,
        long bucketMillis,
        List<String> eventTypes,
        long totalEvents,
        int distinctEventTypes,
        int totalBuckets,
        ActivityOrder order,
        int offset,
        int omittedBuckets,
        boolean hasMoreBuckets,
        List<Bucket> buckets) {

    /** True when the snapshot describes the scope it was asked about. */
    public boolean describes(ActivityScanTarget target) {
        return workspaceId.equals(target.workspaceId())
                && projectId.equals(target.projectId())
                && sessionId.equals(target.sessionId())
                && scanId.equals(target.scanId());
    }

    /** Preserve observed counts when the remote scan disappears before a terminal result is read. */
    public ActivityScanSnapshot failedAt(long finishedAt, String error) {
        return new ActivityScanSnapshot(
                scanId,
                workspaceId,
                projectId,
                sessionId,
                ActivityState.FAILED,
                startedAt,
                finishedAt,
                false,
                coverageKnown,
                sourceErrors,
                filesTotal,
                error,
                startTime,
                endTime,
                bucketMillis,
                eventTypes,
                totalEvents,
                distinctEventTypes,
                totalBuckets,
                order,
                offset,
                omittedBuckets,
                hasMoreBuckets,
                buckets);
    }

    public record Bucket(
            long startTime,
            long endTime,
            long eventCount,
            int distinctEventTypes,
            int omittedTypes,
            List<TypeCount> eventTypes) {
    }

    public record TypeCount(
            String eventType,
            long count) {
    }
}
