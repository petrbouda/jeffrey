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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.activity.ActivityOrder;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventActivityTest {
    private final ActivityRequest request = new ActivityRequest("workspace", "project", "session",
            0, 120000, 60000, Set.of());

    @Test
    void countsMillionsWithoutKeepingEventsAndSeparatesFrequencyFromVariety() {
        EventActivity activity = new EventActivity(request);
        for (int i = 0; i < 1_000_001; i++) {
            activity.add("GC", 1);
        }
        activity.add("CPU", 60000);
        activity.add("Allocation", 119999);
        activity.add("Excluded", 120000);
        activity.add("Excluded", -1);
        var volume = Json.toTree(activity.summary(ActivityOrder.EVENTS, 20, 0));
        assertEquals(1000003, volume.path("totalEvents").asLong());
        assertEquals(3, volume.path("distinctEventTypes").asInt());
        assertEquals(0, volume.path("buckets").get(0).path("startTime").asLong());
        var variety = Json.toTree(activity.summary(ActivityOrder.TYPES, 20, 0)).path("buckets").get(0);
        assertEquals(60000, variety.path("startTime").asLong());
        assertEquals(2, variety.path("distinctEventTypes").asInt());
        assertEquals(2, variety.path("eventCount").asLong());
    }

    /** The upper bound is exclusive even though the replay window that feeds it is inclusive. */
    @Test
    void treatsTheRequestedWindowAsHalfOpen() {
        var activity = new EventActivity(request);
        activity.add("Edge", 0);
        activity.add("Edge", 119999);
        activity.add("Edge", 120000);
        var result = Json.toTree(activity.summary(ActivityOrder.TIME, 20, 0));
        assertEquals(2, result.path("totalEvents").asLong());
        assertEquals(1, result.path("buckets").get(0).path("eventCount").asLong());
        assertEquals(1, result.path("buckets").get(1).path("eventCount").asLong());
        assertEquals(120000, result.path("buckets").get(1).path("endTime").asLong());
    }

    @Test
    void preservesEmptyBucketsAndReportsOmittedDetails() {
        var activity = new EventActivity(request);
        for (int i = 0; i < 12; i++) {
            activity.add("Type" + i, 1);
        }
        var result = Json.toTree(activity.summary(ActivityOrder.EVENTS, 1, 0));
        assertEquals(2, result.path("totalBuckets").asInt());
        assertEquals(1, result.path("omittedBuckets").asInt());
        assertTrue(result.path("hasMoreBuckets").asBoolean());
        assertEquals(12, result.path("buckets").get(0).path("distinctEventTypes").asInt());
        assertEquals(2, result.path("buckets").get(0).path("omittedTypes").asInt());
        assertEquals(0, Json.toTree(activity.summary(ActivityOrder.TIME, 20, 0))
                .path("buckets").get(1).path("eventCount").asLong());
    }

    /**
     * Chronological order over a window wider than one page would otherwise hide every event behind
     * a screenful of empty leading buckets.
     */
    @Test
    void pagingReachesBucketsBeyondTheFirstPage() {
        var wide = new ActivityRequest("workspace", "project", "session", 0, 100_000, 1_000, Set.of());
        var activity = new EventActivity(wide);
        activity.add("Late", 95_000);

        var firstPage = Json.toTree(activity.summary(ActivityOrder.TIME, 20, 0));
        assertEquals(100, firstPage.path("totalBuckets").asInt());
        assertEquals(0, firstPage.path("offset").asInt());
        assertEquals(80, firstPage.path("omittedBuckets").asInt());
        assertTrue(firstPage.path("hasMoreBuckets").asBoolean());
        assertEquals(0, firstPage.path("buckets").get(0).path("eventCount").asLong());

        var lastPage = Json.toTree(activity.summary(ActivityOrder.TIME, 20, 80));
        assertEquals(80, lastPage.path("offset").asInt());
        assertFalse(lastPage.path("hasMoreBuckets").asBoolean());
        assertEquals(95_000, lastPage.path("buckets").get(15).path("startTime").asLong());
        assertEquals(1, lastPage.path("buckets").get(15).path("eventCount").asLong());

        var past = Json.toTree(activity.summary(ActivityOrder.TIME, 20, 100));
        assertEquals(0, past.path("buckets").size());
        assertFalse(past.path("hasMoreBuckets").asBoolean());
    }

    @Test
    void rejectsUnboundedBucketCountsAndStopsAtTheTypeCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityRequest("w", "p", "s", 0, Long.MAX_VALUE, 1, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityRequest("w", "p", "s", Long.MIN_VALUE, Long.MAX_VALUE, 1, Set.of()));
        var activity = new EventActivity(request);
        assertThrows(IllegalArgumentException.class, () -> activity.summary(ActivityOrder.EVENTS, 21, 0));
        assertThrows(IllegalArgumentException.class, () -> activity.summary(ActivityOrder.EVENTS, 20, -1));
        for (int i = 0; i < 512; i++) {
            activity.add("Type" + i, 1);
        }
        assertThrows(IllegalStateException.class, () -> activity.add("Extra", 1));
        assertEquals(512, Json.toTree(activity.summary(ActivityOrder.EVENTS, 20, 0)).path("totalEvents").asLong());
    }
}
