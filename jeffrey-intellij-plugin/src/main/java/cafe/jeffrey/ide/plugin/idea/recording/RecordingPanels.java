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

package cafe.jeffrey.ide.plugin.idea.recording;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Finding the recording panel for a file, which is how anything outside a tab reaches into one.
 *
 * <p>A comparison is set up by an action in the Project view and shown by a panel in an editor tab,
 * and the two have no other way to meet: the panel is created by the platform when a file is opened,
 * so an action cannot construct one and hand it a baseline. It opens the tab and then asks the panel
 * the platform made.
 *
 * <p>Every method here touches editors, so every one of them runs on the EDT.
 */
public final class RecordingPanels {

    private RecordingPanels() {
    }

    /**
     * Opens the file's recording panel, bringing its tab to the front, or null when the platform gave
     * the file to some other editor.
     *
     * <p>Null is a real answer rather than a defensive one: {@code .hprof} is claimed alongside
     * IntelliJ's own heap-dump viewer, and a file can legitimately open in an editor that is not
     * ours.
     */
    public static RecordingPanel open(Project project, VirtualFile file) {
        FileEditor[] editors = FileEditorManager.getInstance(project).openFile(file, true);
        return panelAmong(editors);
    }

    /** The panel for a file already open, or null when the file has no tab. */
    public static RecordingPanel openedFor(Project project, VirtualFile file) {
        return panelAmong(FileEditorManager.getInstance(project).getEditors(file));
    }

    /**
     * The file behind the recording panel the developer is looking at, or null when none is open.
     *
     * <p>The selected editors first and every open one second, so the answer follows the tab in
     * front of them rather than the oldest one they happen to have left open.
     */
    public static VirtualFile openedPanelFile(Project project) {
        FileEditorManager manager = FileEditorManager.getInstance(project);
        VirtualFile selected = fileAmong(manager.getSelectedEditors());
        return selected != null ? selected : fileAmong(manager.getAllEditors());
    }

    private static VirtualFile fileAmong(FileEditor[] editors) {
        for (FileEditor editor : editors) {
            if (editor instanceof RecordingFileEditor recording) {
                return recording.getFile();
            }
        }
        return null;
    }

    private static RecordingPanel panelAmong(FileEditor[] editors) {
        for (FileEditor editor : editors) {
            if (editor instanceof RecordingFileEditor recording) {
                return recording.panel();
            }
        }
        return null;
    }
}
