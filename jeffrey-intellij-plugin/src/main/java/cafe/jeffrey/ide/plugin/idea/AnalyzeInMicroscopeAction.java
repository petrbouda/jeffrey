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
 * <p>One of the plugin's two visible actions, the other being the comparison beside it in the same
 * menu. The rest of it stays headless — no tool window, no toolbar — because a profiler UI belongs in
 * Microscope, and a menu item that leads there is not one.
 */
public final class AnalyzeInMicroscopeAction extends AnAction {

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
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        event.getPresentation().setEnabledAndVisible(AnalysableFiles.isAnalysable(file));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (!AnalysableFiles.isAnalysable(file)) {
            return;
        }
        String baseUrl = JeffreySettings.getInstance().microscopeUrl();
        String encodedPath = URLEncoder.encode(file.getPath(), StandardCharsets.UTF_8);
        BrowserUtil.browse(baseUrl + QUICK_OPEN_PATH + encodedPath);
    }
}
