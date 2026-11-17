/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager.gc.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.GenericRecord;

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
        cachedGCTypes.put(gcId, Json.readString(fields, "type"));
    }
}
