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

package cafe.jeffrey.profile.manager.model.system;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects {@code jdk.ProcessStart} events — subprocesses the JVM launched during the recording — in
 * chronological order.
 */
public class LaunchedProcessesBuilder implements RecordBuilder<GenericRecord, List<LaunchedProcessInfo>> {

    private static final String PID_FIELD = "pid";
    private static final String COMMAND_FIELD = "command";
    private static final String DIRECTORY_FIELD = "directory";
    private static final String EVENT_THREAD_FIELD = "eventThread";

    private final List<LaunchedProcessInfo> processes = new ArrayList<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        processes.add(new LaunchedProcessInfo(
                record.timestampFromStart().toMillis(),
                Json.readLong(fields, PID_FIELD),
                Json.readString(fields, COMMAND_FIELD),
                Json.readString(fields, DIRECTORY_FIELD),
                Json.readString(fields, EVENT_THREAD_FIELD)));
    }

    @Override
    public List<LaunchedProcessInfo> build() {
        return processes;
    }
}
