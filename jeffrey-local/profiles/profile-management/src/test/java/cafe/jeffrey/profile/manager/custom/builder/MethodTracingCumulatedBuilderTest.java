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

package cafe.jeffrey.profile.manager.custom.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.profile.manager.custom.model.method.CumulatedStats;
import cafe.jeffrey.profile.manager.custom.model.method.CumulationMode;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MethodTracingCumulatedBuilder")
class MethodTracingCumulatedBuilderTest {

    private static GenericRecord createMethodRecord(String className, String methodName, long duration) {
        JfrClass clazz = Mockito.mock(JfrClass.class);
        Mockito.when(clazz.className()).thenReturn(className);

        JfrMethod method = Mockito.mock(JfrMethod.class);
        Mockito.when(method.clazz()).thenReturn(clazz);
        Mockito.when(method.className()).thenReturn(className);
        Mockito.when(method.methodName()).thenReturn(methodName);

        return new GenericRecord(
                Type.METHOD_TRACE,
                "Method Trace",
                Instant.now(),
                Duration.ofSeconds(1),
                Duration.ofNanos(duration),
                null,
                method,
                1,
                duration,
                null);
    }

    @Nested
    @DisplayName("ByMethodMode")
    class ByMethodMode {

        @Test
        @DisplayName("Groups records by className#methodName and sorts by totalDuration descending")
        void groupsByMethodAndSortsByDuration() {
            MethodTracingCumulatedBuilder builder = new MethodTracingCumulatedBuilder(CumulationMode.BY_METHOD);

            builder.onRecord(createMethodRecord("com.Foo", "bar", 100));
            builder.onRecord(createMethodRecord("com.Foo", "bar", 200));
            builder.onRecord(createMethodRecord("com.Foo", "baz", 50));

            MethodTracingCumulatedData result = builder.build();

            assertEquals(CumulationMode.BY_METHOD, result.mode());
            assertEquals(3, result.totalInvocations());
            assertEquals(350, result.totalDuration());
            assertEquals(2, result.uniqueCount());

            List<CumulatedStats> items = result.items();
            assertEquals(2, items.size());

            // First item: com.Foo#bar (totalDuration=300, sorted desc)
            CumulatedStats first = items.get(0);
            assertEquals("com.Foo", first.className());
            assertEquals("bar", first.methodName());
            assertEquals(2, first.invocationCount());
            assertEquals(300, first.totalDuration());
            assertEquals(150, first.avgDuration());
            assertEquals(200, first.maxDuration());

            // Second item: com.Foo#baz (totalDuration=50)
            CumulatedStats second = items.get(1);
            assertEquals("com.Foo", second.className());
            assertEquals("baz", second.methodName());
            assertEquals(1, second.invocationCount());
            assertEquals(50, second.totalDuration());
            assertEquals(50, second.avgDuration());
            assertEquals(50, second.maxDuration());
        }
    }

    @Nested
    @DisplayName("ByClassMode")
    class ByClassMode {

        @Test
        @DisplayName("Groups records by className only, aggregating all methods within a class")
        void groupsByClassAggregatingMethods() {
            MethodTracingCumulatedBuilder builder = new MethodTracingCumulatedBuilder(CumulationMode.BY_CLASS);

            builder.onRecord(createMethodRecord("com.Foo", "bar", 100));
            builder.onRecord(createMethodRecord("com.Foo", "baz", 200));
            builder.onRecord(createMethodRecord("com.Bar", "qux", 50));

            MethodTracingCumulatedData result = builder.build();

            assertEquals(CumulationMode.BY_CLASS, result.mode());
            assertEquals(3, result.totalInvocations());
            assertEquals(350, result.totalDuration());
            assertEquals(2, result.uniqueCount());

            List<CumulatedStats> items = result.items();
            assertEquals(2, items.size());

            // First item: com.Foo (totalDuration=300, sorted desc)
            CumulatedStats first = items.get(0);
            assertEquals("com.Foo", first.className());
            assertNull(first.methodName());
            assertEquals(2, first.invocationCount());
            assertEquals(300, first.totalDuration());

            // Second item: com.Bar (totalDuration=50)
            CumulatedStats second = items.get(1);
            assertEquals("com.Bar", second.className());
            assertNull(second.methodName());
            assertEquals(1, second.invocationCount());
            assertEquals(50, second.totalDuration());
        }
    }

    @Nested
    @DisplayName("NullClassNameSkipped")
    class NullClassNameSkipped {

        @Test
        @DisplayName("Records with null className are skipped and not counted")
        void nullClassNameRecordIsSkipped() {
            MethodTracingCumulatedBuilder builder = new MethodTracingCumulatedBuilder(CumulationMode.BY_METHOD);

            builder.onRecord(createMethodRecord(null, "bar", 100));

            MethodTracingCumulatedData result = builder.build();

            assertEquals(0, result.totalInvocations());
            assertEquals(0, result.totalDuration());
            assertEquals(0, result.uniqueCount());
            assertTrue(result.items().isEmpty());
        }
    }

    @Nested
    @DisplayName("TopHundredLimit")
    class TopHundredLimit {

        @Test
        @DisplayName("Items list is limited to top 100 entries even when more unique methods exist")
        void itemsLimitedToTopHundred() {
            MethodTracingCumulatedBuilder builder = new MethodTracingCumulatedBuilder(CumulationMode.BY_METHOD);

            for (int i = 0; i < 150; i++) {
                builder.onRecord(createMethodRecord("com.Class" + i, "method" + i, i + 1));
            }

            MethodTracingCumulatedData result = builder.build();

            assertEquals(150, result.totalInvocations());
            assertEquals(150, result.uniqueCount());
            assertEquals(100, result.items().size());

            // Verify sorted by totalDuration descending: first item should have the highest duration
            CumulatedStats first = result.items().get(0);
            assertEquals(150, first.totalDuration());

            CumulatedStats last = result.items().get(99);
            assertEquals(51, last.totalDuration());
        }
    }
}
