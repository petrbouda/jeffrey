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

package cafe.jeffrey.provisioner.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSettingsJfcTest {

    @Test
    void groupsTheSettingsOfAnEventInOrder() {
        String jfc = EventSettingsJfc.render("jdk.ThreadPark#enabled=true, jdk.SocketRead#threshold=0ms,"
                + "jdk.ThreadPark#threshold=1ms");

        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <configuration version="2.0" label="Jeffrey events" provider="Jeffrey">
                  <event name="jdk.ThreadPark">
                    <setting name="enabled">true</setting>
                    <setting name="threshold">1ms</setting>
                  </event>
                  <event name="jdk.SocketRead">
                    <setting name="enabled">true</setting>
                    <setting name="threshold">0ms</setting>
                  </event>
                </configuration>
                """, jfc);
    }

    /** A method filter is the kind of value that carries XML's special characters. */
    @Test
    void escapesSpecialCharacters() {
        String jfc = EventSettingsJfc.render("jdk.MethodTrace#filter=java.util.List::<init>;a&b");

        assertTrue(jfc.contains(">java.util.List::&lt;init&gt;;a&amp;b<"), jfc);
    }

    /**
     * The recording can only add to what the profiler records: JFR keeps an event on while any
     * recording enables it, so a disable here would be silently ignored.
     */
    @Test
    void refusesToSwitchAnEventOff() {
        assertThrows(IllegalArgumentException.class, () -> EventSettingsJfc.render("jdk.ThreadPark#enabled=false"));
    }

    @Test
    void rejectsAnEntryWithoutASetting() {
        assertThrows(IllegalArgumentException.class, () -> EventSettingsJfc.render("jdk.ThreadPark"));
        assertThrows(IllegalArgumentException.class, () -> EventSettingsJfc.render("jdk.ThreadPark#threshold"));
        assertThrows(IllegalArgumentException.class, () -> EventSettingsJfc.render("#enabled=true"));
    }

    /**
     * Java 25 rate-limits socket and file I/O to 100 events a second, which a threshold does not
     * lift. The lift must be a numeric rate: JFR resolves {@code throttle} to the highest parseable
     * rate across the active recordings, and {@code off} never parses as one, so it would lose to
     * the {@code 100/s} the profiler's recording carries. {@code jdk.FileForce} has no such limit.
     */
    @Test
    void liftsTheRateLimitOnlyWhereThereIsOneToLift() {
        String jfc = EventSettingsJfc.render(TracingJfrEvents.DEFAULT_SETTINGS);

        assertTrue(jfc.contains("""
                  <event name="jdk.SocketRead">
                    <setting name="enabled">true</setting>
                    <setting name="threshold">0ms</setting>
                    <setting name="throttle">1000000/s</setting>
                  </event>
                """), jfc);
        assertFalse(jfc.contains("""
                  <event name="jdk.FileForce">
                    <setting name="enabled">true</setting>
                    <setting name="threshold">0ms</setting>
                    <setting name="throttle">"""), jfc);
    }
}
