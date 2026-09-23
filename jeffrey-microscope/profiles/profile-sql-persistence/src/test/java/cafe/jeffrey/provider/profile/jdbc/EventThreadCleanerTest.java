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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventThreadWithHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventThreadCleanerTest {

    private static EventThreadWithHash platform(long hash, String name, Long osId) {
        return new EventThreadWithHash(hash, new EventThread(name, osId, hash, false));
    }

    private static EventThreadWithHash virtual(long hash, String name) {
        // A virtual thread has no OS thread of its own, so the recording carries no os id for it.
        return new EventThreadWithHash(hash, new EventThread(name, null, hash, true));
    }

    private static Map<Long, String> namesByHash(List<EventThreadWithHash> threads) {
        return threads.stream().collect(Collectors.toMap(
                EventThreadWithHash::hash, thread -> thread.eventThread().name()));
    }

    @Nested
    @DisplayName("Unknown names")
    class UnknownNames {

        @Test
        @DisplayName("a [tid=] name takes the real name recorded for the same os thread")
        void adoptsTheRealNameOfItsOsThread() {
            Map<Long, String> names = namesByHash(new EventThreadCleaner().clean(List.of(
                    platform(1, "[tid=25432]", 25432L),
                    platform(2, "G1 Conc#0", 25432L))));

            assertEquals("G1 Conc#0", names.get(1L));
            assertEquals("G1 Conc#0", names.get(2L));
        }

        @Test
        @DisplayName("a [tid=] name with nothing to adopt is left as it was")
        void keepsUnknownNameWhenNoRealNameExists() {
            Map<Long, String> names = namesByHash(new EventThreadCleaner().clean(List.of(
                    platform(1, "[tid=25432]", 25432L))));

            assertEquals("[tid=25432]", names.get(1L));
        }
    }

    @Nested
    @DisplayName("Real names")
    class RealNames {

        @Test
        @DisplayName("a thread that has a name keeps it, even when a longer one shares its os thread")
        void neverOverwritesARealName() {
            // A carrier that ran under two names is still two threads, and neither is the other.
            Map<Long, String> names = namesByHash(new EventThreadCleaner().clean(List.of(
                    platform(1, "worker", 25432L),
                    platform(2, "ForkJoinPool-1-worker-20", 25432L))));

            assertEquals("worker", names.get(1L));
            assertEquals("ForkJoinPool-1-worker-20", names.get(2L));
        }

        @Test
        @DisplayName("virtual threads keep their own names rather than collapsing onto one")
        void keepsVirtualThreadNames() {
            // Every virtual thread is recorded without an os id. Grouping on that absent id would
            // put all of them in one bucket and rename the lot after whichever name is longest.
            Map<Long, String> names = namesByHash(new EventThreadCleaner().clean(List.of(
                    virtual(1, "tomcat-handler-1"),
                    virtual(2, "tomcat-handler-64"),
                    virtual(3, "tomcat-handler-100"))));

            assertEquals("tomcat-handler-1", names.get(1L));
            assertEquals("tomcat-handler-64", names.get(2L));
            assertEquals("tomcat-handler-100", names.get(3L));
        }

        @Test
        @DisplayName("no thread is lost or duplicated")
        void keepsEveryThread() {
            List<EventThreadWithHash> cleaned = new EventThreadCleaner().clean(List.of(
                    platform(1, "[tid=1]", 1L),
                    platform(2, "main", 1L),
                    platform(3, "db-writer", 2L),
                    virtual(4, "tomcat-handler-1"),
                    virtual(5, "tomcat-handler-100")));

            assertEquals(5, cleaned.size());
            assertEquals(5, namesByHash(cleaned).size());
        }
    }
}
