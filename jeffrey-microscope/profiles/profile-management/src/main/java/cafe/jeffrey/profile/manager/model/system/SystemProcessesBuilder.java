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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collapses periodic {@code jdk.SystemProcess} snapshots into one row per process (keyed by pid,
 * last snapshot wins), ordered by command line for stable display.
 */
public class SystemProcessesBuilder implements RecordBuilder<GenericRecord, List<SystemProcessInfo>> {

    private static final String PID_FIELD = "pid";
    private static final String COMMAND_LINE_FIELD = "commandLine";

    private final Map<String, SystemProcessInfo> processesByPid = new LinkedHashMap<>();

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode fields = record.jsonFields();
        String pid = Json.readString(fields, PID_FIELD);
        if (pid == null) {
            return;
        }
        String commandLine = Json.readString(fields, COMMAND_LINE_FIELD);
        processesByPid.put(pid, new SystemProcessInfo(pid, commandLine == null ? "" : commandLine.strip()));
    }

    @Override
    public List<SystemProcessInfo> build() {
        List<SystemProcessInfo> result = new ArrayList<>(processesByPid.values());
        result.sort(Comparator.comparing(SystemProcessInfo::commandLine, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
