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

package cafe.jeffrey.profile.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import cafe.jeffrey.shared.common.model.Type;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WeightExtractorRegistry")
class WeightExtractorRegistryTest {

    @Nested
    @DisplayName("All registered types resolve to non-null extractors")
    class AllRegisteredTypesResolve {

        static Stream<Type> registeredTypes() {
            return Stream.of(
                    Type.NATIVE_LEAK,
                    Type.METHOD_TRACE,
                    Type.MALLOC,
                    Type.FREE,
                    Type.JAVA_MONITOR_ENTER,
                    Type.JAVA_MONITOR_WAIT,
                    Type.THREAD_PARK,
                    Type.THREAD_SLEEP,
                    Type.OBJECT_ALLOCATION_IN_NEW_TLAB,
                    Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
                    Type.OBJECT_ALLOCATION_SAMPLE,
                    Type.SOCKET_READ,
                    Type.SOCKET_WRITE,
                    Type.FILE_READ,
                    Type.FILE_WRITE,
                    Type.THREAD_ALLOCATION_STATISTICS
            );
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("registeredTypes")
        @DisplayName("resolve() returns non-null for registered type")
        void resolveReturnsNonNull(Type type) {
            WeightExtractor extractor = WeightExtractorRegistry.resolve(type);
            assertNotNull(extractor, "Expected non-null WeightExtractor for type: " + type.code());
        }
    }

    @Nested
    @DisplayName("Unregistered types")
    class UnknownTypeReturnsNull {

        @Test
        @DisplayName("resolve() returns null for a type not in the registry")
        void resolveReturnsNullForUnregisteredType() {
            WeightExtractor extractor = WeightExtractorRegistry.resolve(Type.EXECUTION_SAMPLE);
            assertNull(extractor, "Expected null for unregistered type EXECUTION_SAMPLE");
        }
    }

    @Nested
    @DisplayName("Registry stability")
    class RegistryIsImmutable {

        @Test
        @DisplayName("resolve() returns the same instance on multiple calls")
        void resolveReturnsSameInstance() {
            WeightExtractor first = WeightExtractorRegistry.resolve(Type.THREAD_SLEEP);
            WeightExtractor second = WeightExtractorRegistry.resolve(Type.THREAD_SLEEP);
            assertSame(first, second,
                    "Expected resolve() to return the same WeightExtractor instance on repeated calls");
        }

        @Test
        @DisplayName("resolve() returns stable instances for all registered types")
        void resolveReturnsStableInstancesForAllTypes() {
            for (Type type : new Type[]{
                    Type.NATIVE_LEAK, Type.METHOD_TRACE, Type.MALLOC, Type.FREE,
                    Type.JAVA_MONITOR_ENTER, Type.JAVA_MONITOR_WAIT,
                    Type.THREAD_PARK, Type.THREAD_SLEEP,
                    Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_OUTSIDE_TLAB,
                    Type.OBJECT_ALLOCATION_SAMPLE,
                    Type.SOCKET_READ, Type.SOCKET_WRITE,
                    Type.FILE_READ, Type.FILE_WRITE,
                    Type.THREAD_ALLOCATION_STATISTICS}) {

                WeightExtractor first = WeightExtractorRegistry.resolve(type);
                WeightExtractor second = WeightExtractorRegistry.resolve(type);
                assertSame(first, second,
                        "Expected stable instance for type: " + type.code());
            }
        }
    }
}
