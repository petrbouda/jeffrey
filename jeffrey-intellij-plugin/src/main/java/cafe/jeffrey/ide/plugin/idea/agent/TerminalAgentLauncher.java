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
