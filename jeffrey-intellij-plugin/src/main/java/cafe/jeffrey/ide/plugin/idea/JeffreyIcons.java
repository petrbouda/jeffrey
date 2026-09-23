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

package cafe.jeffrey.ide.plugin.idea;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

/**
 * The plugin's one icon, loaded once.
 *
 * <p>The same asset the "Analyze in Microscope" action carries, deliberately: a recording in the
 * project tree and the menu item that opens it are the same thing, and two drawings of a flame graph
 * would only invite the reader to look for a difference that is not there. The action declares it by
 * path in {@code plugin.xml}; this is the handle for the code that needs it as an {@link Icon}.
 */
public final class JeffreyIcons {

    public static final Icon FILE = IconLoader.getIcon("/icons/jeffrey-icon.svg", JeffreyIcons.class);

    /** The heap dump's counterpart to the flame: an object graph, in the same flat orange. */
    public static final Icon HEAP_DUMP = IconLoader.getIcon("/icons/heap-dump.svg", JeffreyIcons.class);

    private JeffreyIcons() {
    }
}
