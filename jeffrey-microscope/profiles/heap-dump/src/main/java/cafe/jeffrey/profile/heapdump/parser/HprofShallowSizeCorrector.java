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
import org.eclipse.collections.api.map.primitive.LongIntMap;
import org.eclipse.collections.api.map.primitive.LongObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.api.tuple.primitive.LongIntPair;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

/**
 * Phase 6 — corrects instance.shallow_size to match the JVM's allocated size:
 *
 * <ol>
 *   <li>Subtracts compressed-oops over-count for INSTANCE rows. The HPROF
 *       file encodes every OBJECT field as {@code idSize} bytes (8 on a
 *       64-bit dump), but at runtime each compressed OOP occupies
 *       {@code oopSize} (4). For every class we count OBJECT fields along
 *       the full super-class chain and subtract
 *       {@code chainOopCount * (idSize - oopSize)} from each instance.</li>
 *   <li>Rounds shallow_size up to {@link InstanceLayout#objectAlignment()}
 *       for every row (instances and arrays alike) — the JVM aligns each
 *       allocation to {@code MinObjAlignment}, default 8.</li>
 * </ol>
 *
 * <p>Both corrections collapse to no-ops when they're not needed:
 * uncompressed 64-bit / 32-bit heaps have {@code oopOverheadDelta() == 0},
 * and rows already aligned to the boundary are left untouched.
 */
public final class HprofShallowSizeCorrector {

    private HprofShallowSizeCorrector() {
    }

    public static void apply(
            HeapDumpDatabaseClient client,
            LongObjectMap<HprofRecord.ClassDump> classDumps,
            InstanceLayout layout) {
        int oopDelta = layout.oopOverheadDelta();
        int alignment = layout.objectAlignment();

        if (oopDelta > 0 && !classDumps.isEmpty()) {
            LongIntMap chainOopByClass = computeChainOopCounts(classDumps);
            client.execute(HeapDumpStatement.CREATE_TEMP_CLASS_CHAIN_OOP,
                    "CREATE TEMP TABLE _class_chain_oop (class_id BIGINT, oop_count INTEGER)");
            client.withAppender(HeapDumpStatement.APPEND_CLASS_CHAIN_OOP, "_class_chain_oop", app -> {
                long rows = 0;
                for (LongIntPair e : chainOopByClass.keyValuesView()) {
                    if (e.getTwo() == 0) {
                        continue; // skip zero rows to keep the table tight
                    }
                    app.beginRow();
                    app.append(e.getOne());
                    app.append(e.getTwo());
                    app.endRow();
                    rows++;
                }
                return rows;
            });
            client.update(HeapDumpStatement.UPDATE_INSTANCE_SHALLOW_SIZE_OOPS,
                    "UPDATE instance SET shallow_size = shallow_size - c.oop_count * ? "
                            + "FROM _class_chain_oop c "
                            + "WHERE instance.class_id = c.class_id "
                            + "  AND instance.record_kind = " + HprofIndex.RECORD_KIND_INSTANCE,
                    oopDelta);
            client.execute(HeapDumpStatement.DROP_TEMP_CLASS_CHAIN_OOP, "DROP TABLE _class_chain_oop");
        }

        // Round every row up to objectAlignment. Arrays are already aligned by
        // Pass B's append helpers, but instances were written without alignment
        // and may have just become unaligned again after the OOP-delta
        // subtraction. Use modular arithmetic (no division) so the math stays
        // in INTEGER domain — DuckDB's `/` promotes to floating point on
        // bound parameters.
        client.update(HeapDumpStatement.UPDATE_INSTANCE_SHALLOW_SIZE_ALIGN,
                "UPDATE instance SET shallow_size = shallow_size + ((? - shallow_size % ?) % ?) "
                        + "WHERE shallow_size % ? <> 0",
                alignment, alignment, alignment, alignment);
    }

    private static LongIntMap computeChainOopCounts(
            LongObjectMap<HprofRecord.ClassDump> classDumps) {
        MutableLongIntMap memo = new LongIntHashMap(classDumps.size());
        // forEachKey accepts a LongProcedure (primitive long), avoiding a
        // Long boxing per class entry.
        classDumps.forEachKey(classId -> chainOopCount(classId, classDumps, memo));
        return memo;
    }

    private static int chainOopCount(
            long classId,
            LongObjectMap<HprofRecord.ClassDump> classDumps,
            MutableLongIntMap memo) {
        // LongIntHashMap stores 0 as the absent-value sentinel by default, so
        // we use containsKey to disambiguate "cached 0" from "not yet computed".
        if (memo.containsKey(classId)) {
            return memo.get(classId);
        }
        HprofRecord.ClassDump cd = classDumps.get(classId);
        if (cd == null) {
            memo.put(classId, 0);
            return 0;
        }
        int ownCount = 0;
        for (int type : cd.instanceFieldTypes()) {
            if (type == HprofTag.BasicType.OBJECT) {
                ownCount++;
            }
        }
        long superId = cd.superClassId();
        int total = superId == 0L
                ? ownCount
                : ownCount + chainOopCount(superId, classDumps, memo);
        memo.put(classId, total);
        return total;
    }
}
