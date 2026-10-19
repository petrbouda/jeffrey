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

package cafe.jeffrey.profile.manager;

import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.profile.common.exceptions.ThrowFrame;
import cafe.jeffrey.profile.common.exceptions.ThrowOrigin;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionMessageCount;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionSiteStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysis;
import cafe.jeffrey.profile.manager.model.exceptions.ThrowAnalysisBuilder;
import cafe.jeffrey.provider.profile.api.GenericRecord;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileExceptionRepository;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.ThrowCountRecord;
import cafe.jeffrey.provider.profile.api.ThrowMessageRecord;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptionsManagerImpl")
class ExceptionsManagerImplTest {

    @Mock
    ProfileEventRepository eventRepository;

    @Mock
    ProfileEventStreamRepository eventStreamRepository;

    @Mock
    ProfileExceptionRepository exceptionRepository;

    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2024-01-01T00:01:00Z");

    private static final ProfileInfo PROFILE_INFO = new ProfileInfo(
            "test-id", "project-1", "workspace-1", "test-profile",
            null, START, END, START, true, false, null);

    private ExceptionsManagerImpl manager() {
        return new ExceptionsManagerImpl(PROFILE_INFO, eventRepository, eventStreamRepository, exceptionRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubStreaming(GenericRecord... records) {
        when(eventStreamRepository.genericStreaming(any(), any())).thenAnswer(invocation -> {
            RecordBuilder<GenericRecord, Object> builder = invocation.getArgument(1);
            for (GenericRecord record : records) {
                builder.onRecord(record);
            }
            return builder.build();
        });
    }

    private static GenericRecord record(Type type, long secondsFromStart, ObjectNode fields) {
        return new GenericRecord(
                type, "label", START,
                Duration.ofSeconds(secondsFromStart), null,
                null, null, 0L, 0L, fields);
    }

    private static ObjectNode statisticsFields(long throwables) {
        ObjectNode node = Json.createObject();
        node.put("throwables", throwables);
        return node;
    }

    @Nested
    @DisplayName("timeline()")
    class Timeline {

        @Test
        @DisplayName("Emits deltas between consecutive cumulative samples")
        void emitsDeltas() {
            stubStreaming(
                    record(Type.EXCEPTION_STATISTICS, 1, statisticsFields(100)),
                    record(Type.EXCEPTION_STATISTICS, 2, statisticsFields(150)),
                    record(Type.EXCEPTION_STATISTICS, 3, statisticsFields(150)),
                    record(Type.EXCEPTION_STATISTICS, 4, statisticsFields(170)));

            TimeseriesData timeline = manager().timeline();

            assertEquals(1, timeline.series().size());
            SingleSerie serie = timeline.series().getFirst();
            assertEquals("Exceptions / sec", serie.name());
            assertEquals(0, valueAt(serie, 1));   // baseline sample contributes nothing
            assertEquals(50, valueAt(serie, 2));
            assertEquals(0, valueAt(serie, 3));
            assertEquals(20, valueAt(serie, 4));
        }

        @Test
        @DisplayName("Clamps negative deltas to zero (JVM restart in one recording)")
        void clampsNegativeDeltas() {
            stubStreaming(
                    record(Type.EXCEPTION_STATISTICS, 1, statisticsFields(500)),
                    record(Type.EXCEPTION_STATISTICS, 2, statisticsFields(10)));

            SingleSerie serie = manager().timeline().series().getFirst();
            assertEquals(0, valueAt(serie, 2));
        }

        private long valueAt(SingleSerie serie, long second) {
            return serie.data().stream()
                    .filter(point -> point.get(0) == second)
                    .map(point -> point.get(1))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static final String IO_EXCEPTION = "java.io.IOException";
    private static final String CNFE = "java.lang.ClassNotFoundException";
    private static final String STACK_OVERFLOW = "java.lang.StackOverflowError";

    private static final long APP_STACK = 11L;
    private static final long OTHER_APP_STACK = 12L;
    private static final long LOADER_STACK = 13L;
    private static final long ERROR_STACK = 14L;

    private static final ThrowFrame PLACE_ORDER = new ThrowFrame("com.acme.orders.OrderService", "place", 42);

    /** IOException thrown from OrderService.place, reached from two different callers. */
    private static final Map<Long, List<ThrowFrame>> STACKS = Map.of(
            APP_STACK, List.of(
                    new ThrowFrame("java.lang.Throwable", "<init>", 266),
                    new ThrowFrame(IO_EXCEPTION, "<init>", 62),
                    PLACE_ORDER,
                    new ThrowFrame("com.acme.orders.OrderController", "post", 20)),
            OTHER_APP_STACK, List.of(
                    new ThrowFrame("java.lang.Throwable", "<init>", 266),
                    new ThrowFrame(IO_EXCEPTION, "<init>", 62),
                    PLACE_ORDER,
                    new ThrowFrame("com.acme.orders.OrderJob", "run", 31)),
            LOADER_STACK, List.of(
                    new ThrowFrame("java.lang.Throwable", "<init>", 266),
                    new ThrowFrame(IO_EXCEPTION, "<init>", 62),
                    new ThrowFrame("jdk.internal.loader.URLClassPath$JarLoader", "getResource", 1053)),
            ERROR_STACK, List.of(
                    new ThrowFrame("java.lang.Throwable", "<init>", 266),
                    new ThrowFrame(STACK_OVERFLOW, "<init>", 54),
                    new ThrowFrame("com.acme.orders.Recursive", "descend", 7)));

    private void recorded() {
        when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(true);
        when(exceptionRepository.throwCounts()).thenReturn(List.of(
                new ThrowCountRecord(IO_EXCEPTION, false, APP_STACK, 1L, 5),
                new ThrowCountRecord(IO_EXCEPTION, false, APP_STACK, 2L, 1),
                new ThrowCountRecord(IO_EXCEPTION, false, OTHER_APP_STACK, 1L, 2),
                new ThrowCountRecord(IO_EXCEPTION, false, LOADER_STACK, 3L, 4),
                new ThrowCountRecord(CNFE, false, null, 1L, 3),
                new ThrowCountRecord(STACK_OVERFLOW, true, ERROR_STACK, 2L, 1)));
        when(exceptionRepository.throwMessages()).thenReturn(List.of(
                new ThrowMessageRecord(IO_EXCEPTION, APP_STACK, "connection reset", 4),
                new ThrowMessageRecord(IO_EXCEPTION, OTHER_APP_STACK, "connection reset", 1),
                new ThrowMessageRecord(IO_EXCEPTION, LOADER_STACK, "zip END header not found", 4),
                new ThrowMessageRecord(CNFE, null, "com.acme.Optional", 3)));
        when(exceptionRepository.throwStacktraces()).thenReturn(STACKS);
    }

    private static <T> T only(List<T> rows, Predicate<T> filter) {
        List<T> matching = rows.stream().filter(filter).toList();
        assertEquals(1, matching.size(), matching.toString());
        return matching.getFirst();
    }

    @Nested
    @DisplayName("topTypes()")
    class TopTypes {

        @Test
        @DisplayName("Returns empty without reading throws when neither throw event is present")
        void emptyWhenAbsent() {
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(false);
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(false);

            assertTrue(manager().topTypes().isEmpty());
            verify(exceptionRepository, never()).throwCounts();
        }

        @Test
        @DisplayName("Groups by class across stacks, with exact distinct threads and summed messages")
        void groupsByClass() {
            recorded();

            List<ExceptionTypeStat> types = manager().topTypes();

            assertEquals(List.of(IO_EXCEPTION, CNFE, STACK_OVERFLOW),
                    types.stream().map(ExceptionTypeStat::thrownClass).toList());

            ExceptionTypeStat io = types.getFirst();
            assertEquals(12, io.count());
            assertFalse(io.error());
            assertEquals(3, io.threadCount());
            assertEquals(List.of(
                    new ExceptionMessageCount("connection reset", 5),
                    new ExceptionMessageCount("zip END header not found", 4)), io.messages());
            assertEquals(0, io.omittedMessages());
            assertEquals(8, io.applicationCount());
            assertEquals(4, io.jvmInternalCount());

            ExceptionTypeStat error = types.get(2);
            assertTrue(error.error());
            assertEquals(1, error.count());
        }

        @Test
        @DisplayName("Resolution failures count as JVM internal even without a stack")
        void classRuleWithoutStack() {
            recorded();

            ExceptionTypeStat cnfe = only(manager().topTypes(), type -> CNFE.equals(type.thrownClass()));

            assertEquals(0, cnfe.applicationCount());
            assertEquals(3, cnfe.jvmInternalCount());
        }
    }

    @Nested
    @DisplayName("sites()")
    class Sites {

        @Test
        @DisplayName("Merges stacks that reach the same throw and keeps the commonest stack")
        void mergesByThrowingFrame() {
            recorded();

            ExceptionSiteStat placeOrder = only(manager().sites(), site -> PLACE_ORDER.equals(site.throwingFrame()));

            assertEquals(IO_EXCEPTION, placeOrder.thrownClass());
            assertEquals(ThrowOrigin.APPLICATION, placeOrder.origin());
            assertEquals(8, placeOrder.count());
            assertEquals(2, placeOrder.threadCount());
            assertEquals(TraceIds.hex(APP_STACK), placeOrder.stacktraceId());
            assertEquals(List.of(new ExceptionMessageCount("connection reset", 5)), placeOrder.messages());
        }

        @Test
        @DisplayName("A throw from a class loader is JVM internal whatever its class")
        void loaderThrowIsJvmInternal() {
            recorded();

            ExceptionSiteStat loader = only(manager().sites(),
                    site -> IO_EXCEPTION.equals(site.thrownClass()) && site.origin() == ThrowOrigin.JVM_INTERNAL);

            assertEquals(4, loader.count());
            assertEquals("jdk.internal.loader.URLClassPath$JarLoader", loader.throwingFrame().className());
        }

        @Test
        @DisplayName("A site with no stack has no frame and no stack id")
        void siteWithoutStack() {
            recorded();

            ExceptionSiteStat cnfe = only(manager().sites(), site -> CNFE.equals(site.thrownClass()));

            assertNull(cnfe.throwingFrame());
            assertNull(cnfe.stacktraceId());
            assertEquals(ThrowOrigin.JVM_INTERNAL, cnfe.origin());
        }

        @Test
        @DisplayName("The analysis carries the stack behind each site")
        void analysisCarriesStacks() {
            recorded();

            ThrowAnalysis analysis = manager().analysis();
            ExceptionSiteStat placeOrder = only(analysis.sites(), site -> PLACE_ORDER.equals(site.throwingFrame()));

            assertEquals(STACKS.get(APP_STACK), analysis.stackOf(placeOrder));
        }

        @Test
        @DisplayName("Caps messages and reports how many were left out")
        void capsMessages() {
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(true);
            int distinct = ThrowAnalysisBuilder.MESSAGES_LIMIT + 7;
            when(exceptionRepository.throwCounts()).thenReturn(
                    List.of(new ThrowCountRecord(IO_EXCEPTION, false, APP_STACK, 1L, distinct)));
            when(exceptionRepository.throwMessages()).thenReturn(IntStream.range(0, distinct)
                    .mapToObj(index -> new ThrowMessageRecord(IO_EXCEPTION, APP_STACK, "order " + index, 1))
                    .toList());
            when(exceptionRepository.throwStacktraces()).thenReturn(STACKS);

            ExceptionSiteStat site = manager().sites().getFirst();

            assertEquals(ThrowAnalysisBuilder.MESSAGES_LIMIT, site.messages().size());
            assertEquals(7, site.omittedMessages());
        }
    }

    @Nested
    @DisplayName("overview()")
    class Overview {

        @Test
        @DisplayName("Combines cumulative gauge with sampled-type aggregates and the origin split")
        void aggregates() {
            when(eventRepository.latestJsonFields(Type.EXCEPTION_STATISTICS))
                    .thenReturn(Optional.of(statisticsFields(9177)));
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(true);
            recorded();

            ExceptionsOverview overview = manager().overview();

            assertEquals(9177, overview.totalThrowables());
            assertEquals(15, overview.sampledThrowCount());
            assertEquals(1, overview.errorCount());
            assertEquals(3, overview.distinctTypes());
            assertTrue(overview.hasExceptionThrowEvents());
            assertTrue(overview.hasErrorThrowEvents());
            assertEquals(9, overview.applicationThrowCount());
            assertEquals(7, overview.jvmInternalThrowCount());
        }

        @Test
        @DisplayName("Falls back to zeros when no exception events exist")
        void zerosWhenAbsent() {
            when(eventRepository.latestJsonFields(Type.EXCEPTION_STATISTICS)).thenReturn(Optional.empty());
            when(eventRepository.containsEventType(Type.JAVA_EXCEPTION_THROW)).thenReturn(false);
            when(eventRepository.containsEventType(Type.JAVA_ERROR_THROW)).thenReturn(false);

            ExceptionsOverview overview = manager().overview();

            assertEquals(0, overview.totalThrowables());
            assertEquals(0, overview.sampledThrowCount());
            assertEquals(0, overview.distinctTypes());
            assertEquals(0, overview.applicationThrowCount());
            assertFalse(overview.hasExceptionThrowEvents());
        }
    }
}
