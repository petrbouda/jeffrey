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

package cafe.jeffrey.profile.manager.model.nativememory;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData.LibraryOperation;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData.Operation;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("NativeLibraryActivityBuilder")
class NativeLibraryActivityBuilderTest {

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final long MS = 1_000_000L;

    private static GenericRecord rec(Type type, long secondsFromStart, long durationNanos, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), Duration.ofNanos(durationNanos),
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode fields(String name, boolean success, String errorMessage) {
        ObjectNode node = Json.createObject();
        node.put("name", name);
        node.put("success", success);
        if (errorMessage != null) {
            node.put("errorMessage", errorMessage);
        }
        return node;
    }

    @Test
    @DisplayName("Counts loads/unloads, tracks failures and slowest load, builds timelines")
    void aggregates() {
        NativeLibraryActivityBuilder builder = new NativeLibraryActivityBuilder(new RelativeTimeRange(0, 10_000), 10);
        builder.onRecord(rec(Type.NATIVE_LIBRARY_LOAD, 1, 5 * MS, fields("libfast.so", true, null)));
        builder.onRecord(rec(Type.NATIVE_LIBRARY_LOAD, 1, 40 * MS, fields("libslow.so", true, null)));
        builder.onRecord(rec(Type.NATIVE_LIBRARY_LOAD, 2, 0, fields("libmissing.so", false, "cannot open shared object file")));
        builder.onRecord(rec(Type.NATIVE_LIBRARY_UNLOAD, 3, 1 * MS, fields("libfast.so", true, null)));

        NativeLibraryActivityData data = builder.build();

        assertEquals(3, data.header().totalLoads());
        assertEquals(1, data.header().failedLoads());
        assertEquals(1, data.header().totalUnloads());
        assertEquals(40 * MS, data.header().slowestLoadNanos());
        assertEquals(45 * MS, data.header().totalLoadNanos());
        assertEquals("libslow.so", data.header().slowestLibrary());

        // Operations are sorted by duration desc; slowest load first.
        assertEquals("libslow.so", data.operations().getFirst().name());
        assertEquals(Operation.LOAD, data.operations().getFirst().operation());

        // Failed load carries its error message and false success.
        LibraryOperation failed = data.operations().stream()
                .filter(op -> op.name().equals("libmissing.so"))
                .findFirst()
                .orElseThrow();
        assertFalse(failed.success());
        assertEquals("cannot open shared object file", failed.errorMessage());

        // Successful op has no error message.
        assertNull(data.operations().getFirst().errorMessage());

        assertEquals("Loads", data.timeline().series().getFirst().name());
        assertEquals("Unloads", data.timeline().series().get(1).name());
    }

    @Test
    @DisplayName("Caps the number of returned operations")
    void capsOperations() {
        NativeLibraryActivityBuilder builder = new NativeLibraryActivityBuilder(new RelativeTimeRange(0, 10_000), 2);
        for (int i = 0; i < 5; i++) {
            builder.onRecord(rec(Type.NATIVE_LIBRARY_LOAD, 1, i * MS, fields("lib" + i + ".so", true, null)));
        }

        NativeLibraryActivityData data = builder.build();

        assertEquals(5, data.header().totalLoads());
        assertEquals(2, data.operations().size());
        assertEquals("lib4.so", data.operations().getFirst().name());
    }
}
