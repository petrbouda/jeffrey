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
