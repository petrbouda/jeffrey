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
