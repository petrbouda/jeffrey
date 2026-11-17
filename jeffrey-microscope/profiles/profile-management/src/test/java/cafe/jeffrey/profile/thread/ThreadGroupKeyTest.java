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

package cafe.jeffrey.profile.thread;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ThreadGroupKeyTest {

    @Nested
    class PoolsThatNumberTheirWorkers {

        @Test
        void collapseOnTheTrailingNumber() {
            assertEquals("http-nio-8080-exec-*", ThreadGroupKey.of("http-nio-8080-exec-17"));
            assertEquals("http-nio-8080-exec-*", ThreadGroupKey.of("http-nio-8080-exec-3"));
        }

        @Test
        void handleTheSeparatorsTheJdkUses() {
            assertEquals("worker-*", ThreadGroupKey.of("worker-12"));
            assertEquals("worker#*", ThreadGroupKey.of("worker#12"));
            assertEquals("worker_*", ThreadGroupKey.of("worker_12"));
            assertEquals("Thread*", ThreadGroupKey.of("Thread12"));
        }

        /**
         * Only the last number goes. A pool's own number says which executor a thread belongs to, so
         * dropping it would merge two unrelated pools into one lane.
         */
        @Test
        void keepAnEarlierNumberThatIdentifiesThePool() {
            assertEquals("pool-3-thread-*", ThreadGroupKey.of("pool-3-thread-1"));
            assertNotEquals(
                    ThreadGroupKey.of("pool-3-thread-1"),
                    ThreadGroupKey.of("pool-4-thread-1"));
        }

        @Test
        void collapseForkJoinWorkers() {
            assertEquals(
                    "ForkJoinPool.commonPool-worker-*",
                    ThreadGroupKey.of("ForkJoinPool.commonPool-worker-3"));
        }
    }

    @Nested
    class ThreadsThatShareANameExactly {

        /**
         * The recording that prompted this: 351 threads all called the same thing, no numbers to
         * strip. They group on the name itself.
         */
        @Test
        void groupOnTheNameItself() {
            assertEquals("oracleApp:connection-adder", ThreadGroupKey.of("oracleApp:connection-adder"));
            assertEquals(
                    ThreadGroupKey.of("oracleApp:connection-adder"),
                    ThreadGroupKey.of("oracleApp:connection-adder"));
        }

        @Test
        void leaveUnnumberedNamesAlone() {
            assertEquals("Keep-Alive-Timer", ThreadGroupKey.of("Keep-Alive-Timer"));
            assertEquals("main", ThreadGroupKey.of("main"));
            assertEquals("Notification Thread", ThreadGroupKey.of("Notification Thread"));
        }

        /**
         * A name that is nothing but digits has no stem to group under, so it stays as it is rather
         * than collapsing every such thread into a single nameless lane.
         */
        @Test
        void leaveANameThatIsOnlyDigitsAlone() {
            assertEquals("12345", ThreadGroupKey.of("12345"));
        }
    }

    @Nested
    class MissingNames {

        @Test
        void fallBackToAPlaceholder() {
            assertEquals("<unnamed>", ThreadGroupKey.of(null));
            assertEquals("<unnamed>", ThreadGroupKey.of(""));
            assertEquals("<unnamed>", ThreadGroupKey.of("   "));
        }
    }
}
