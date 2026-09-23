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
