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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JfrProfilerPluginBridgeTest {

    private final JfrProfilerPluginBridge bridge = new JfrProfilerPluginBridge("http://localhost:4243");

    @Test
    void isAlwaysEnabledOnceConfigured() {
        assertTrue(bridge.isEnabled());
    }

    @Test
    void targetStatusIsAutoLinkedButNotSelectable() {
        IdeTargetStatus status = bridge.targetStatus("p1");

        // Single-URL connection: always "linked", and there is no window to pick or disconnect.
        assertTrue(status.linked());
        assertFalse(status.selectable());
    }
}
