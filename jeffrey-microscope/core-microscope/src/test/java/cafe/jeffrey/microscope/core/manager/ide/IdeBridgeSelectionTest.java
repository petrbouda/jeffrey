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

package cafe.jeffrey.microscope.core.manager.ide;

import cafe.jeffrey.microscope.core.configuration.AppConfiguration;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class IdeBridgeSelectionTest {

    private static final String BASE_URL = "http://localhost:4243";

    @Nested
    class ModeParsing {

        @Test
        void blankOrMissingDefaultsToJeffreyPluginMode() {
            assertEquals(IdeMode.JEFFREY_PLUGIN, IdeMode.fromProperty(null));
            assertEquals(IdeMode.JEFFREY_PLUGIN, IdeMode.fromProperty(""));
            assertEquals(IdeMode.JEFFREY_PLUGIN, IdeMode.fromProperty("   "));
        }

        @Test
        void resolvesKnownValuesCaseInsensitively() {
            assertEquals(IdeMode.JEFFREY_PLUGIN, IdeMode.fromProperty("jeffrey-plugin"));
            assertEquals(IdeMode.JEFFREY_PLUGIN, IdeMode.fromProperty(" Jeffrey-Plugin "));
            assertEquals(IdeMode.JFR_PROFILER_PLUGIN, IdeMode.fromProperty("jfr-profiler-plugin"));
            assertEquals(IdeMode.JFR_PROFILER_PLUGIN, IdeMode.fromProperty(" Jfr-Profiler-Plugin "));
        }

        @Test
        void unknownValueFailsFast() {
            assertThrows(IllegalArgumentException.class, () -> IdeMode.fromProperty("bogus"));
        }
    }

    @Nested
    class BridgeSelection {

        private static final int PORT_START = 63342;
        private static final int PORT_END = 63362;

        private final AppConfiguration configuration = new AppConfiguration();

        /**
         * Only the first-party bridge reaches the store, and only when a link is read or written —
         * which none of these tests does. What is under test is which bridge the mode selects.
         */
        private final MicroscopeCorePersistenceProvider persistence =
                mock(MicroscopeCorePersistenceProvider.class, RETURNS_DEEP_STUBS);

        @Test
        void jeffreyPluginModeWiresJeffreyPluginBridge() {
            IdeBridge bridge = configuration.ideBridge(persistence, "jeffrey-plugin", BASE_URL, PORT_START, PORT_END);
            assertInstanceOf(JeffreyPluginBridge.class, bridge);
            assertTrue(bridge.isEnabled());
        }

        @Test
        void blankModeWiresJeffreyPluginBridge() {
            IdeBridge bridge = configuration.ideBridge(persistence, "", BASE_URL, PORT_START, PORT_END);
            assertInstanceOf(JeffreyPluginBridge.class, bridge);
        }

        @Test
        void jfrProfilerModeWiresJfrProfilerPluginBridge() {
            IdeBridge bridge = configuration.ideBridge(persistence, "jfr-profiler-plugin", BASE_URL, PORT_START, PORT_END);
            assertInstanceOf(JfrProfilerPluginBridge.class, bridge);
            assertTrue(bridge.isEnabled());
        }

        @Test
        void unknownModeFailsFast() {
            assertThrows(IllegalArgumentException.class,
                    () -> configuration.ideBridge(persistence, "bogus", BASE_URL, PORT_START, PORT_END));
        }
    }
}
