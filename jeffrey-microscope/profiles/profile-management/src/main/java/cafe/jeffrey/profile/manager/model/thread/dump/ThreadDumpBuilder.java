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
