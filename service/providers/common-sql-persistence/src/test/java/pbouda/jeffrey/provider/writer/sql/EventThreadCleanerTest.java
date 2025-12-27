/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.api.model.EventThread;
import pbouda.jeffrey.provider.api.model.writer.EventThreadWithHash;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventThreadCleanerTest {

    private EventThreadCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new EventThreadCleaner();
    }

    @Nested
    class EmptyAndSingleThread {

        @Test
        void emptyListReturnsEmptyList() {
            List<EventThreadWithHash> result = cleaner.clean(List.of());

            assertTrue(result.isEmpty());
        }

        @Test
        void singleThreadWithValidNameUnchanged() {
            EventThreadWithHash thread = createThread(1L, "main", 100L, 1L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(thread));

            assertEquals(1, result.size());
            assertEquals("main", result.getFirst().eventThread().name());
        }

        @Test
        void singleThreadWithUnknownNameUnchanged() {
            EventThreadWithHash thread = createThread(1L, "[tid=12345]", 100L, 1L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(thread));

            assertEquals(1, result.size());
            assertEquals("[tid=12345]", result.getFirst().eventThread().name());
        }
    }

    @Nested
    class UnknownNameFixing {

        @Test
        void unknownNameReplacedWithValidNameFromSameOsId() {
            EventThreadWithHash unknownThread = createThread(1L, "[tid=12345]", 100L, 1L);
            EventThreadWithHash validThread = createThread(2L, "GC Thread#0", 100L, 2L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(unknownThread, validThread));

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(t -> t.eventThread().name().equals("GC Thread#0")));
        }

        @Test
        void longestValidNameUsedForGroup() {
            EventThreadWithHash short1 = createThread(1L, "GC", 100L, 1L);
            EventThreadWithHash short2 = createThread(2L, "GC Thread", 100L, 2L);
            EventThreadWithHash longest = createThread(3L, "GC Thread#0 (ParallelGC)", 100L, 3L);
            EventThreadWithHash unknown = createThread(4L, "[tid=999]", 100L, 4L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(short1, short2, longest, unknown));

            assertEquals(4, result.size());
            assertTrue(result.stream().allMatch(t ->
                    t.eventThread().name().equals("GC Thread#0 (ParallelGC)")));
        }

        @Test
        void allUnknownNamesKeptIfNoValidNameExists() {
            EventThreadWithHash unknown1 = createThread(1L, "[tid=111]", 100L, 1L);
            EventThreadWithHash unknown2 = createThread(2L, "[tid=222]", 100L, 2L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(unknown1, unknown2));

            assertEquals(2, result.size());
            // Names should be unchanged when all are unknown
            List<String> names = result.stream().map(t -> t.eventThread().name()).toList();
            assertTrue(names.contains("[tid=111]") || names.contains("[tid=222]"));
        }
    }

    @Nested
    class GroupingByOsId {

        @Test
        void threadsGroupedByOsIdIndependently() {
            // Group 1: osId = 100
            EventThreadWithHash group1Unknown = createThread(1L, "[tid=100]", 100L, 1L);
            EventThreadWithHash group1Valid = createThread(2L, "Worker-1", 100L, 2L);

            // Group 2: osId = 200
            EventThreadWithHash group2Unknown = createThread(3L, "[tid=200]", 200L, 3L);
            EventThreadWithHash group2Valid = createThread(4L, "Worker-2", 200L, 4L);

            List<EventThreadWithHash> result = cleaner.clean(
                    List.of(group1Unknown, group1Valid, group2Unknown, group2Valid));

            assertEquals(4, result.size());

            // Group 1 threads should have "Worker-1"
            long group1Count = result.stream()
                    .filter(t -> t.eventThread().osId() == 100L)
                    .filter(t -> t.eventThread().name().equals("Worker-1"))
                    .count();
            assertEquals(2, group1Count);

            // Group 2 threads should have "Worker-2"
            long group2Count = result.stream()
                    .filter(t -> t.eventThread().osId() == 200L)
                    .filter(t -> t.eventThread().name().equals("Worker-2"))
                    .count();
            assertEquals(2, group2Count);
        }

        @Test
        void differentOsIdGroupsNotAffectEachOther() {
            EventThreadWithHash validGroup1 = createThread(1L, "Thread-A", 100L, 1L);
            EventThreadWithHash unknownGroup2 = createThread(2L, "[tid=200]", 200L, 2L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(validGroup1, unknownGroup2));

            assertEquals(2, result.size());

            // Group 1 keeps its name
            EventThreadWithHash g1 = result.stream()
                    .filter(t -> t.eventThread().osId() == 100L)
                    .findFirst().orElseThrow();
            assertEquals("Thread-A", g1.eventThread().name());

            // Group 2 keeps its unknown name (no valid alternative)
            EventThreadWithHash g2 = result.stream()
                    .filter(t -> t.eventThread().osId() == 200L)
                    .findFirst().orElseThrow();
            assertEquals("[tid=200]", g2.eventThread().name());
        }
    }

    @Nested
    class HashPreservation {

        @Test
        void originalHashPreservedAfterCleaning() {
            EventThreadWithHash thread1 = createThread(12345L, "[tid=100]", 100L, 1L);
            EventThreadWithHash thread2 = createThread(67890L, "ValidName", 100L, 2L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(thread1, thread2));

            assertTrue(result.stream().anyMatch(t -> t.hash() == 12345L));
            assertTrue(result.stream().anyMatch(t -> t.hash() == 67890L));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void threadNameStartingWithTidButNotUnknownPattern() {
            // "[tid=" must be at the start to be considered unknown
            EventThreadWithHash thread = createThread(1L, "Thread [tid=123] info", 100L, 1L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(thread));

            assertEquals(1, result.size());
            assertEquals("Thread [tid=123] info", result.getFirst().eventThread().name());
        }

        @Test
        void multipleThreadsSameOsIdDifferentJavaId() {
            EventThreadWithHash t1 = createThread(1L, "pool-1-thread-1", 100L, 10L);
            EventThreadWithHash t2 = createThread(2L, "pool-1-thread-1-extended-name", 100L, 11L);
            EventThreadWithHash t3 = createThread(3L, "[tid=100]", 100L, 12L);

            List<EventThreadWithHash> result = cleaner.clean(List.of(t1, t2, t3));

            assertEquals(3, result.size());
            // All should have the longest valid name
            assertTrue(result.stream().allMatch(t ->
                    t.eventThread().name().equals("pool-1-thread-1-extended-name")));
        }

        @Test
        void jitCompilerThreadNaming() {
            // Real-world scenario: JIT compiler threads often have unknown names
            EventThreadWithHash jitUnknown = createThread(1L, "[tid=54321]", 500L, null);
            EventThreadWithHash jitNamed = createThread(2L, "C2 CompilerThread0", 500L, null);

            List<EventThreadWithHash> result = cleaner.clean(List.of(jitUnknown, jitNamed));

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(t ->
                    t.eventThread().name().equals("C2 CompilerThread0")));
        }

        @Test
        void gcThreadNaming() {
            // Real-world scenario: GC threads
            EventThreadWithHash gcUnknown = createThread(1L, "[tid=11111]", 300L, null);
            EventThreadWithHash gcNamed = createThread(2L, "GC Thread#5", 300L, null);

            List<EventThreadWithHash> result = cleaner.clean(List.of(gcUnknown, gcNamed));

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(t ->
                    t.eventThread().name().equals("GC Thread#5")));
        }
    }

    private static EventThreadWithHash createThread(long hash, String name, Long osId, Long javaId) {
        return new EventThreadWithHash(hash, new EventThread(name, osId, javaId, false));
    }
}
