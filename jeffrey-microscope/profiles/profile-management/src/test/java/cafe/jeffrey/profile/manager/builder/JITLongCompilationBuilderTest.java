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

package cafe.jeffrey.profile.manager.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.common.event.JITCompilerType;
import cafe.jeffrey.profile.common.event.JITLongCompilation;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.microscope.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JITLongCompilationBuilder")
class JITLongCompilationBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static GenericRecord createRecord(long compileId, String compiler, String method,
                                              long compileLevel, boolean succeded, boolean isOsr,
                                              long codeSize, long duration) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("compileId", compileId);
        fields.put("compiler", compiler);
        fields.put("method", method);
        fields.put("compileLevel", compileLevel);
        fields.put("succeded", succeded);
        fields.put("isOsr", isOsr);
        fields.put("codeSize", codeSize);
        fields.put("duration", duration);

        return new GenericRecord(
                Type.COMPILATION,
                "Compilation",
                Instant.EPOCH,
                Duration.ZERO,
                Duration.ofNanos(duration),
                null,
                null,
                1,
                0,
                fields
        );
    }

    @Nested
    @DisplayName("Top N longest compilations")
    class TopNLongest {

        @Test
        @DisplayName("Returns top 3 longest compilations sorted in descending order from 5 records")
        void returnsTopThreeSortedDescending() {
            var builder = new JITLongCompilationBuilder(3);

            builder.onRecord(createRecord(1, "c2", "com.example.A.a()", 4, true, false, 512, 10));
            builder.onRecord(createRecord(2, "c2", "com.example.B.b()", 4, true, false, 768, 50));
            builder.onRecord(createRecord(3, "c2", "com.example.C.c()", 4, true, false, 1024, 30));
            builder.onRecord(createRecord(4, "c2", "com.example.D.d()", 4, true, false, 2048, 90));
            builder.onRecord(createRecord(5, "c2", "com.example.E.e()", 4, true, false, 1536, 70));

            List<JITLongCompilation> result = builder.build();

            assertEquals(3, result.size());
            assertEquals(90, result.get(0).duration());
            assertEquals(70, result.get(1).duration());
            assertEquals(50, result.get(2).duration());
        }
    }

    @Nested
    @DisplayName("Fewer records than limit")
    class FewerThanLimit {

        @Test
        @DisplayName("Returns all records when fewer than limit are provided")
        void returnsAllRecordsWhenFewerThanLimit() {
            var builder = new JITLongCompilationBuilder(5);

            builder.onRecord(createRecord(1, "c1", "com.example.A.a()", 3, true, false, 256, 100));
            builder.onRecord(createRecord(2, "c2", "com.example.B.b()", 4, true, false, 512, 200));

            List<JITLongCompilation> result = builder.build();

            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("Empty input")
    class EmptyInput {

        @Test
        @DisplayName("Returns an empty list when no records are provided")
        void returnsEmptyListWhenNoRecords() {
            var builder = new JITLongCompilationBuilder(3);

            List<JITLongCompilation> result = builder.build();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("JSON fields preserved in deserialization")
    class JsonFieldsPreserved {

        @Test
        @DisplayName("JITLongCompilation fields are correctly deserialized from JSON")
        void fieldsCorrectlyDeserialized() {
            var builder = new JITLongCompilationBuilder(5);

            builder.onRecord(createRecord(42, "c2", "com.test.MyClass.foo()", 4, true, false, 1024, 50000000));

            List<JITLongCompilation> result = builder.build();

            assertEquals(1, result.size());

            JITLongCompilation compilation = result.getFirst();
            assertEquals(42, compilation.compileId());
            assertEquals(JITCompilerType.C2, compilation.compiler());
            assertEquals("com.test.MyClass.foo()", compilation.method());
            assertEquals(4, compilation.compileLevel());
            assertTrue(compilation.succeded());
            assertFalse(compilation.isOsr());
            assertEquals(1024, compilation.codeSize());
            assertEquals(50000000, compilation.duration());
        }
    }
}
