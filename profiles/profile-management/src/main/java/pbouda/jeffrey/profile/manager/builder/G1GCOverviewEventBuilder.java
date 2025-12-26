/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.profile.common.event.GarbageCollectorType;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.profile.manager.model.gc.GCEvent;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;

import java.util.Optional;

public class G1GCOverviewEventBuilder extends ConcurrentGCOverviewEventBuilder {

    public G1GCOverviewEventBuilder(RelativeTimeRange timeRange, int maxLongestPauses) {
        super(GarbageCollectorType.G1,
                timeRange,
                maxLongestPauses,
                Type.G1_GARBAGE_COLLECTION,
                Type.OLD_GARBAGE_COLLECTION);
    }

    @Override
    protected void processYoungGCEvent(GenericRecord record, ObjectNode fields, String eventType) {
        super.processYoungGCEvent(record, fields, eventType);

        long gcId = Json.readLong(fields, "gcId");
        Optional<GCEvent> foundGcEvent = longestPauses.stream()
                .filter(pause -> pause.getGcId() == gcId)
                .findFirst();

        cachedGCTypes.put(gcId, Json.readString(fields, "type"));
    }
}
