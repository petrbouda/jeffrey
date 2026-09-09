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

import cafe.jeffrey.ide.plugin.idea.JeffreyIcons;
import com.intellij.icons.AllIcons;

import javax.swing.Icon;
import java.util.Map;

/**
 * The icons the panel's markup can reference.
 *
 * <p>Registered with the HTML kit by key, and reached from the markup as {@code <icon src="key"/>} —
 * the element name and the {@code src} attribute are what the platform's icon extension looks for.
 *
 * <p>Platform icons rather than our own artwork, apart from the flame: a panel inside the IDE should
 * be drawn from the IDE's own set, so it matches whatever icon theme the developer runs and picks up
 * their light/dark variants for free.
 */
final class PanelIcons {

    // Map.ofEntries rather than Map.of: the latter caps at ten pairs, which this reached when the
    // differential views arrived.
    static final Map<String, Icon> BY_KEY = Map.ofEntries(
            Map.entry("flame", JeffreyIcons.FILE),
            Map.entry("heap", JeffreyIcons.HEAP_DUMP),
            Map.entry("analysis", AllIcons.General.InspectionsEye),
            Map.entry("subsecond", AllIcons.Vcs.History),
            Map.entry("allocations", AllIcons.Actions.ProfileMemory),
            Map.entry("gc", AllIcons.Actions.GC),
            Map.entry("threads", AllIcons.Debugger.Threads),
            Map.entry("jit", AllIcons.Actions.Lightning),
            Map.entry("events", AllIcons.Nodes.DataTables),
            Map.entry("traces", AllIcons.FileTypes.Diagram),
            Map.entry("diff", AllIcons.Actions.Diff));

    private PanelIcons() {
    }
}
