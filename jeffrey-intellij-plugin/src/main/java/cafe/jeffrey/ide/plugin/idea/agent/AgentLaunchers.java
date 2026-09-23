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
