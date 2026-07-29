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
