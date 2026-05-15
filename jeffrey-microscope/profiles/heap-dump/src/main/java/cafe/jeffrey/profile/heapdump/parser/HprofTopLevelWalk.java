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
package cafe.jeffrey.profile.heapdump.parser;

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * Phase 1 — top-level HPROF walk. Streams every STRING record into the
 * {@code string} table while populating an in-memory {@link TopLevelData}
 * envelope that downstream phases (stack traces, Pass A, Pass B, string
 * content, metadata) read from.
 */
public final class HprofTopLevelWalk {

    private HprofTopLevelWalk() {
    }

    public static TopLevelData walk(HprofMappedFile file, HeapDumpDatabaseClient client) {
        TopLevelData data = new TopLevelData();
        client.withAppender(HeapDumpStatement.APPEND_STRING, "string", stringApp -> {
            HprofTopLevelReader.read(file, new HprofTopLevelReader.Listener() {
                @Override
                public void onRecord(HprofRecord.Top record) {
                    data.recordCount++;
                    switch (record) {
                        case HprofRecord.HprofString s -> {
                            try {
                                stringApp.beginRow();
                                stringApp.append(s.stringId());
                                stringApp.append(new String(s.utf8(), StandardCharsets.UTF_8));
                                stringApp.endRow();
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                            data.stringPool.put(s.stringId(), s.utf8());
                            data.stringCount++;
                        }
                        case HprofRecord.LoadClass lc -> data.loadClassByClassId.put(lc.classId(), lc);
                        case HprofRecord.HeapDumpRegion hdr -> data.regions.add(hdr);
                        case HprofRecord.StackFrame sf -> data.stackFrames.add(sf);
                        case HprofRecord.StackTrace st -> data.stackTraces.add(st);
                        case HprofRecord.OpaqueTop ignored -> {
                        }
                    }
                }

                @Override
                public void onWarning(ParseWarning warning) {
                    data.warnings.add(warning);
                }
            });
            return data.stringCount;
        });
        return data;
    }
}
