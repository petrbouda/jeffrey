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
import org.duckdb.DuckDBAppender;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.appendNullableId;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.primArrayClassId;
import static cafe.jeffrey.profile.heapdump.parser.HprofAppenderUtils.primArrayName;

/**
 * Phase 4 — Pass A. Sequential walk over every HPROF region processing only
 * CLASS_DUMP sub-records, writing the {@code class} and
 * {@code class_instance_field} tables. Cheap (~22 K class records on a typical
 * 7.6 M-instance heap) and must finish before Pass B reads the map. Returns a
 * {@link ClassDumpIndex} that downstream phases (Pass B, shallow correction,
 * string content, metadata) read from.
 */
public final class HprofClassDumpWalker {

    private HprofClassDumpWalker() {
    }

    public static ClassDumpIndex walk(
            HprofMappedFile file, HeapDumpDatabaseClient client, TopLevelData top) {
        Map<Long, HprofRecord.ClassDump> byId = new HashMap<>();
        List<ParseWarning> warnings = new ArrayList<>();
        Set<Long> writtenClassIds = new HashSet<>();
        long[] classCount = {0L};

        client.withAppenderPair(HeapDumpStatement.APPEND_CLASS, "class", "class_instance_field",
                (classApp, fieldApp) -> {
                    // Seed synthetic primitive-array class rows so PRIMITIVE_ARRAY_DUMP
                    // instances can join to a real class name.
                    classCount[0] += appendSyntheticPrimitiveArrayClasses(classApp);

                    for (HprofRecord.HeapDumpRegion region : top.regions) {
                        HprofSubRecordReader.read(file, region.fileOffset(), region.byteLength(),
                                new HprofSubRecordReader.Listener() {
                                    @Override
                                    public void onRecord(HprofRecord.Sub sub) {
                                        if (sub instanceof HprofRecord.ClassDump cd) {
                                            try {
                                                if (writtenClassIds.add(cd.classId())) {
                                                    appendClass(classApp, cd, top);
                                                    appendInstanceFields(fieldApp, cd, top);
                                                    classCount[0]++;
                                                    byId.put(cd.classId(), cd);
                                                }
                                            } catch (SQLException e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }

                                    @Override
                                    public void onWarning(ParseWarning warning) {
                                        warnings.add(warning);
                                    }
                                });
                    }
                    return classCount[0];
                });
        return new ClassDumpIndex(byId, classCount[0], warnings);
    }

    private static void appendClass(
            DuckDBAppender app, HprofRecord.ClassDump cd, TopLevelData top) throws SQLException {
        HprofRecord.LoadClass lc = top.loadClassByClassId.get(cd.classId());
        String rawName;
        int classSerial;
        if (lc == null) {
            rawName = "<unresolved-class:0x" + Long.toHexString(cd.classId()) + ">";
            classSerial = 0;
        } else {
            byte[] nameBytes = top.stringPool.get(lc.nameStringId());
            rawName = nameBytes != null
                    ? new String(nameBytes, StandardCharsets.UTF_8)
                    : "<unresolved-name:0x" + Long.toHexString(lc.nameStringId()) + ">";
            classSerial = lc.classSerial();
        }

        // Detect array-class by the raw HPROF prefix '[' before normalisation
        // (the user-facing form ends in "[]" so we'd lose the cheap signal otherwise).
        boolean isArray = !rawName.isEmpty() && rawName.charAt(0) == '[';
        String name = ClassNameFormatter.userFacing(rawName);

        app.beginRow();
        app.append(cd.classId());
        app.append(classSerial);
        app.append(name);
        app.append(isArray);
        appendNullableId(app, cd.superClassId());
        appendNullableId(app, cd.classloaderId());
        appendNullableId(app, cd.signersId());
        appendNullableId(app, cd.protectionDomainId());
        app.append(cd.instanceSize());
        // static_fields_size: not tracked separately yet; populate with 0 to satisfy NOT NULL.
        app.append(0);
        app.append(cd.fileOffset());
        app.endRow();
    }

    /**
     * Inserts eight synthetic class rows — one per HPROF primitive basic-type —
     * so that {@link HprofRecord.PrimitiveArrayDump} instances can be assigned
     * a real {@code class_id} and surface in every class-keyed view (histogram,
     * dominator tree, leak suspects, …) with their proper {@code byte[]} /
     * {@code int[]} / … names. Synthetic ids live in the deeply-negative range
     * allocated by {@link HprofAppenderUtils#primArrayClassId} and cannot
     * collide with real HPROF object ids.
     */
    private static long appendSyntheticPrimitiveArrayClasses(DuckDBAppender app) throws SQLException {
        long inserted = 0;
        for (int elementType = 4; elementType <= 11; elementType++) {
            String name = primArrayName(elementType);
            if (name == null) {
                continue;
            }
            app.beginRow();
            app.append(primArrayClassId(elementType));
            app.append(0); // class_serial — synthetic, no HPROF serial
            app.append(name);
            app.append(true); // is_array
            app.appendNull(); // super_class_id
            app.appendNull(); // classloader_id (bootstrap)
            app.appendNull(); // signers_id
            app.appendNull(); // protection_domain_id
            app.append(0); // instance_size — variable; per-instance shallow_size is on the instance row
            app.append(0); // static_fields_size
            app.append(-1L); // file_offset — no HPROF backing record
            app.endRow();
            inserted++;
        }
        return inserted;
    }

    private static void appendInstanceFields(
            DuckDBAppender app, HprofRecord.ClassDump cd, TopLevelData top) throws SQLException {
        long[] nameIds = cd.instanceFieldNameIds();
        int[] types = cd.instanceFieldTypes();
        for (int i = 0; i < types.length; i++) {
            byte[] nameBytes = top.stringPool.get(nameIds[i]);
            String name = nameBytes != null
                    ? new String(nameBytes, StandardCharsets.UTF_8)
                    : "<unresolved-field-name:0x" + Long.toHexString(nameIds[i]) + ">";
            app.beginRow();
            app.append(cd.classId());
            app.append(i);
            app.append(name);
            app.append((byte) types[i]);
            app.endRow();
        }
    }
}
