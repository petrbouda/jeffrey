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

package cafe.jeffrey.profile.manager.model.thread.dump;

import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects raw {@code jdk.ThreadDump} occurrences (offset + dump text), capped. Parsing and aggregation
 * are done downstream by {@link ThreadDumpParser} / {@link ThreadDumpAnalyzer}, keeping this builder lean.
 */
public class ThreadDumpBuilder implements RecordBuilder<GenericRecord, List<RawDump>> {

    private static final String RESULT_FIELD = "result";

    private final int maxDumps;
    private final List<RawDump> dumps = new ArrayList<>();

    public ThreadDumpBuilder(int maxDumps) {
        if (maxDumps <= 0) {
            throw new IllegalArgumentException("maxDumps must be positive: " + maxDumps);
        }
        this.maxDumps = maxDumps;
    }

    @Override
    public void onRecord(GenericRecord record) {
        if (dumps.size() >= maxDumps) {
            return;
        }
        String text = Json.readString(record.jsonFields(), RESULT_FIELD);
        dumps.add(new RawDump(record.timestampFromStart().toMillis(), text == null ? "" : text));
    }

    @Override
    public List<RawDump> build() {
        return dumps;
    }
}
