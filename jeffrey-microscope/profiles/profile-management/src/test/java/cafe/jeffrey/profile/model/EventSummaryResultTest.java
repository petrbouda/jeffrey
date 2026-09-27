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

package cafe.jeffrey.profile.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("EventSummaryResult")
class EventSummaryResultTest {

    private static final String CODE = "jdk.ExecutionSample";
    private static final String LABEL = "Method Profiling Sample";

    private static EventSummaryResult.SingleResult withSettings(Map<String, String> settings) {
        return new EventSummaryResult.SingleResult(
                CODE, LABEL, null, null, 10, 10, false, Map.of(), settings);
    }

    @Nested
    @DisplayName("Settings")
    class Settings {

        @Test
        @DisplayName("keeps the settings that carry a value")
        void keepsTheSettingsThatCarryAValue() {
            Map<String, String> settings = Map.of("period", "10 ms", "enabled", "true");

            assertEquals(settings, withSettings(settings).settings());
        }

        @Test
        @DisplayName("drops a setting the recording left without a value instead of failing")
        void dropsASettingWithoutAValue() {
            Map<String, String> settings = new HashMap<>();
            settings.put("period", "10 ms");
            settings.put("threshold", null);

            assertEquals(Map.of("period", "10 ms"), withSettings(settings).settings());
        }

        @Test
        @DisplayName("treats absent settings as none")
        void treatsAbsentSettingsAsNone() {
            assertEquals(Map.of(), withSettings(null).settings());
        }
    }
}
