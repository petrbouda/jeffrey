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

    static final Map<String, Icon> BY_KEY = Map.of(
            "flame", JeffreyIcons.FILE,
            "analysis", AllIcons.General.InspectionsEye,
            "subsecond", AllIcons.Vcs.History,
            "allocations", AllIcons.Actions.ProfileMemory,
            "gc", AllIcons.Actions.GC,
            "threads", AllIcons.Debugger.Threads,
            "jit", AllIcons.Actions.Lightning,
            "events", AllIcons.Nodes.DataTables,
            "traces", AllIcons.FileTypes.Diagram);

    private PanelIcons() {
    }
}
