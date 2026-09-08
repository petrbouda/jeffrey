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

package cafe.jeffrey.ide.plugin.idea.recording;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * The questions the panel asks a state before drawing it: where "Open in Microscope" lands, and
 * whether the auto analysis is still on its way.
 */
public class RecordingStateTest {

    @Test
    public void landsARecordingOnTheDashboard() {
        assertEquals("dashboard", summary(RecordingState.Kind.RECORDING).landingPath());
    }

    @Test
    public void landsAHeapDumpOnItsOverview() {
        assertEquals("heap-dump/overview", summary(RecordingState.Kind.HEAP_DUMP).landingPath());
    }

    private static RecordingState.ProfileSummary summary(RecordingState.Kind kind) {
        return new RecordingState.ProfileSummary(kind, "profile", null, null, false, false, List.of(), List.of());
    }
}
