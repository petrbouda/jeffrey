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

import com.intellij.openapi.project.Project;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.nio.file.Path;

/**
 * Opens a terminal tab and types the command into it.
 *
 * <p>The only class here that touches the Terminal plugin, and it is instantiated behind
 * {@link AgentLaunchers#available()} so a disabled Terminal plugin cannot turn into a
 * {@code NoClassDefFoundError} on a panel that would otherwise have worked.
 *
 * <p>The command is <i>sent</i> rather than run as a process: the developer sees exactly what was
 * typed, keeps the session afterwards to carry on the conversation, and the agent inherits the shell
 * they configured rather than a bare environment this plugin assembled.
 */
final class TerminalAgentLauncher implements AgentLauncher {

    private static final String TAB_NAME = "Analyse profile";

    @Override
    public void launch(Project project, Path workingDirectory, String command) {
        TerminalWidget widget = TerminalToolWindowManager.getInstance(project)
                .createShellWidget(workingDirectory.toString(), TAB_NAME, true, true);
        widget.sendCommandToExecute(command);
    }
}
