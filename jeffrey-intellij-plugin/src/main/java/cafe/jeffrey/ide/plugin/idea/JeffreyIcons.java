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

    private JeffreyIcons() {
    }
}
