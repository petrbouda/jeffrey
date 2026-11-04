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

package cafe.jeffrey.shared.persistence.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JfrPoolStatisticsPeriodicRecorderTest {

    /** The Flight Recorder refuses one hook twice; a second application context in the JVM must still start. */
    @Test
    void registeringASecondTimeIsANoOp() {
        JfrPoolStatisticsPeriodicRecorder.registerToFlightRecorder();

        assertDoesNotThrow(JfrPoolStatisticsPeriodicRecorder::registerToFlightRecorder);
    }
}
