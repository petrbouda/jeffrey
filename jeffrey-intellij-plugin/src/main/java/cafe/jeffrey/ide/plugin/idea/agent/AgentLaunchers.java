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

package cafe.jeffrey.ide.plugin.idea.agent;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;

/**
 * Picks how a command gets run: a terminal tab where the IDE has one, the clipboard otherwise.
 *
 * <p>The Terminal plugin is bundled in every IDE, but a developer can switch it off, and the plugin
 * declares only an <i>optional</i> dependency on it for exactly that reason — the same care the
 * resolver takes in not depending on the Kotlin plugin or Git4Idea. The check here is what keeps that
 * promise at runtime.
 */
public final class AgentLaunchers {

    private static final Logger LOG = Logger.getInstance(AgentLaunchers.class);

    private static final PluginId TERMINAL = PluginId.getId("org.jetbrains.plugins.terminal");

    private AgentLaunchers() {
    }

    public static AgentLauncher current() {
        if (available()) {
            try {
                return new TerminalAgentLauncher();
            } catch (LinkageError e) {
                // Present but not loadable — a broken install, or a platform that moved the class.
                // Copying the command still gets the developer where they were going.
                LOG.info("The Terminal plugin is installed but its classes did not load", e);
            }
        }
        return new ClipboardAgentLauncher();
    }

    private static boolean available() {
        return PluginManagerCore.getPlugin(TERMINAL) != null && !PluginManagerCore.isDisabled(TERMINAL);
    }
}
