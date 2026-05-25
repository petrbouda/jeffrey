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

package cafe.jeffrey.microscope.core.manager.ide;

import cafe.jeffrey.microscope.core.configuration.AppConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        @Test
        void jeffreyPluginModeWiresJeffreyPluginBridge() {
            IdeBridge bridge = configuration.ideBridge("jeffrey-plugin", BASE_URL, PORT_START, PORT_END);
            assertInstanceOf(JeffreyPluginBridge.class, bridge);
            assertTrue(bridge.isEnabled());
        }

        @Test
        void blankModeWiresJeffreyPluginBridge() {
            IdeBridge bridge = configuration.ideBridge("", BASE_URL, PORT_START, PORT_END);
            assertInstanceOf(JeffreyPluginBridge.class, bridge);
        }

        @Test
        void jfrProfilerModeWiresJfrProfilerPluginBridge() {
            IdeBridge bridge = configuration.ideBridge("jfr-profiler-plugin", BASE_URL, PORT_START, PORT_END);
            assertInstanceOf(JfrProfilerPluginBridge.class, bridge);
            assertTrue(bridge.isEnabled());
        }

        @Test
        void unknownModeFailsFast() {
            assertThrows(IllegalArgumentException.class,
                    () -> configuration.ideBridge("bogus", BASE_URL, PORT_START, PORT_END));
        }
    }
}
