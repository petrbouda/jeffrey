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
