/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.nmt;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

/**
 * Single-pass aggregate over {@code jdk.NativeMemoryUsageTotal}: the latest reserved/committed and
 * the peak committed seen across the recording. Requires a chronological stream for "last" semantics.
 * (There is no NMT peak event, so the peak is derived here.)
 */
public class NmtTotalStatsBuilder implements RecordBuilder<GenericRecord, NmtTotalStatsBuilder.NmtTotalStats> {

    private static final String RESERVED_FIELD = "reserved";
    private static final String COMMITTED_FIELD = "committed";

    public record NmtTotalStats(long lastReserved, long lastCommitted, long peakCommitted) {
    }

    private long lastReserved;
    private long lastCommitted;
    private long peakCommitted;

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        lastReserved = Math.max(0, Json.readLong(fields, RESERVED_FIELD));
        lastCommitted = Math.max(0, Json.readLong(fields, COMMITTED_FIELD));
        peakCommitted = Math.max(peakCommitted, lastCommitted);
    }

    @Override
    public NmtTotalStats build() {
        return new NmtTotalStats(lastReserved, lastCommitted, peakCommitted);
    }
}
