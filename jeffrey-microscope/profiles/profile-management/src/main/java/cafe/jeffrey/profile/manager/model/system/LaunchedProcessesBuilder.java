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
