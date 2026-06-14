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

package cafe.jeffrey.profile.manager.model.thread.dump;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.ParsedThread;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.ThreadLock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ThreadDumpParser")
class ThreadDumpParserTest {

    private static final String DUMP = String.join("\n",
            "2024-01-01 00:00:00",
            "Full thread dump OpenJDK 64-Bit Server VM:",
            "",
            "\"main\" #1 prio=5 os_prio=0 cpu=10ms tid=0x01 nid=0x1 runnable [0x7f00]",
            "   java.lang.Thread.State: RUNNABLE",
            "\tat app.Main.work(Main.java:10)",
            "\tat app.Main.main(Main.java:5)",
            "",
            "\"worker-1\" #12 prio=5 tid=0x02 nid=0x2 waiting for monitor entry [0x7f10]",
            "   java.lang.Thread.State: BLOCKED (on object monitor)",
            "\tat app.Service.handle(Service.java:42)",
            "\t- waiting to lock <0x000000abcd> (a app.Lock)",
            "",
            "\"pool-1-thread-3\" #20 daemon prio=5 tid=0x03 nid=0x3 waiting on condition [0x7f20]",
            "   java.lang.Thread.State: TIMED_WAITING (parking)",
            "\tat jdk.internal.misc.Unsafe.park(Native Method)",
            "\t- parking to wait for <0x000000ef01> (a java.util.concurrent.SynchronousQueue)",
            "",
            "\"GC Thread#0\" os_prio=0 tid=0x04 nid=0x4 runnable",
            "");

    @Test
    @DisplayName("Parses thread names, states, frames and locks")
    void parsesThreads() {
        ParsedDump dump = ThreadDumpParser.parse(1000, DUMP);

        assertEquals(4, dump.threads().size());

        ParsedThread main = dump.threads().getFirst();
        assertEquals("main", main.name());
        assertEquals(ThreadState.RUNNABLE, main.state());
        assertEquals("app.Main.work(Main.java:10)", main.topFrame());
        assertEquals(2, main.frames().size());

        ParsedThread worker = dump.threads().get(1);
        assertEquals(ThreadState.BLOCKED, worker.state());
        assertEquals(1, worker.locks().size());
        assertEquals(ThreadLock.Kind.WAITING_TO_LOCK, worker.locks().getFirst().kind());
        assertEquals("0x000000abcd", worker.locks().getFirst().monitorId());
        assertEquals("app.Lock", worker.locks().getFirst().monitorClass());

        ParsedThread pool = dump.threads().get(2);
        assertEquals(ThreadState.TIMED_WAITING, pool.state());
        assertEquals("pool-1-thread", pool.group());
        assertEquals(ThreadLock.Kind.PARKING_TO_WAIT, pool.locks().getFirst().kind());

        // A native thread with no Thread.State line maps to UNKNOWN.
        ParsedThread gc = dump.threads().get(3);
        assertEquals(ThreadState.UNKNOWN, gc.state());
    }

    @Test
    @DisplayName("Extracts a JVM-reported deadlock and its involved threads")
    void parsesDeadlock() {
        String dumpText = String.join("\n",
                "Found one Java-level deadlock:",
                "=============================",
                "\"Thread-A\":",
                "  waiting to lock monitor 0x01 (object 0x0a, a java.lang.Object),",
                "  which is held by \"Thread-B\"",
                "\"Thread-B\":",
                "  waiting to lock monitor 0x02 (object 0x0b, a java.lang.Object),",
                "  which is held by \"Thread-A\"",
                "",
                "Java stack information for the threads listed above:",
                "===================================================",
                "\"Thread-A\" #10 prio=5 tid=0x0a nid=0xa waiting for monitor entry",
                "   java.lang.Thread.State: BLOCKED (on object monitor)",
                "\tat app.A.run(A.java:1)",
                "");

        ParsedDump dump = ThreadDumpParser.parse(2000, dumpText);

        assertEquals(1, dump.deadlocks().size());
        ParsedDump.Deadlock deadlock = dump.deadlocks().getFirst();
        assertTrue(deadlock.description().contains("Java-level deadlock"));
        assertEquals(List.of("Thread-A", "Thread-B"), deadlock.involvedThreads());
    }

    @Test
    @DisplayName("Handles empty / null text without throwing")
    void handlesEmpty() {
        assertEquals(0, ThreadDumpParser.parse(0, "").threads().size());
        assertEquals(0, ThreadDumpParser.parse(0, null).threads().size());
    }
}
