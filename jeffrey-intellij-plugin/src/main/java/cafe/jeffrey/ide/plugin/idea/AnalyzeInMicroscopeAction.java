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

import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Sends the selected recording or heap dump to Jeffrey Microscope for analysis.
 *
 * <p>The other direction of the integration. Everything else here answers questions Microscope asks
 * about the code; this hands Microscope a file the developer is looking at, which is the step that
 * otherwise means finding the artifact in a file dialog after every profiled run.
 *
 * <p>It opens {@code /quick-open?path=<absolute path>} in a browser rather than uploading anything:
 * Microscope runs on this same machine and reads the file itself, so a multi-gigabyte recording is
 * never copied, and a Microscope that is not running fails as a browser error the developer can see
 * rather than as a silent no-op.
 *
 * <p>This is the plugin's only visible action. The rest of it stays headless — no tool window, no
 * toolbar — because a profiler UI belongs in Microscope, and a menu item that leads there is not one.
 */
public final class AnalyzeInMicroscopeAction extends AnAction {

    private static final Set<String> ANALYSABLE_EXTENSIONS = Set.of("jfr", "hprof");

    private static final String QUICK_OPEN_PATH = "/quick-open?path=";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Shown only for a file Microscope can actually read. An action that is always visible and
     * usually fails teaches the developer to ignore it.
     */
    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(analysable(event.getData(CommonDataKeys.VIRTUAL_FILE)));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (!analysable(file)) {
            return;
        }
        String baseUrl = JeffreySettings.getInstance().microscopeUrl();
        String encodedPath = URLEncoder.encode(file.getPath(), StandardCharsets.UTF_8);
        BrowserUtil.browse(baseUrl + QUICK_OPEN_PATH + encodedPath);
    }

    private static boolean analysable(VirtualFile file) {
        if (file == null || file.isDirectory()) {
            return false;
        }
        String extension = file.getExtension();
        return extension != null
                && ANALYSABLE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }
}
