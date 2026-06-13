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
