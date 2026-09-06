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

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;

import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;

/**
 * Copies the command and says so, for an IDE with no terminal to open.
 *
 * <p>Degrading to this rather than hiding the buttons is the point: remote development, a disabled
 * Terminal plugin and a developer who lives in tmux are all cases where the panel still knows the one
 * thing worth knowing — the exact command — and the only thing it cannot do is run it.
 */
final class ClipboardAgentLauncher implements AgentLauncher {

    private static final String GROUP = "Jeffrey Microscope";
    private static final String TITLE = "Command copied";
    private static final String BODY =
            "The IDE has no terminal available, so the command was copied instead. Paste it into a shell:";

    @Override
    public void launch(Project project, Path workingDirectory, String command) {
        CopyPasteManager.getInstance().setContents(new StringSelection(command));

        NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP)
                .createNotification(TITLE, BODY + "\n" + command, NotificationType.INFORMATION)
                .notify(project);
    }
}
