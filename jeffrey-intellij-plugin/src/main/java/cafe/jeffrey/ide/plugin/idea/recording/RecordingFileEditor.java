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
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;

/**
 * The editor tab a recording opens in.
 *
 * <p>Thin on purpose: the tab is a frame around {@link RecordingPanel}, and everything worth reading
 * is in there. It holds no state of its own — {@link #getState} answers the platform's shared "no
 * state" instance, because reopening a recording should re-ask Microscope rather than restore what
 * it said last session, which may since have been analysed, deleted or rebuilt.
 */
final class RecordingFileEditor extends UserDataHolderBase implements FileEditor {

    private final VirtualFile file;
    private final RecordingPanel panel;

    RecordingFileEditor(Project project, VirtualFile file) {
        this.file = file;
        this.panel = new RecordingPanel(project, Path.of(file.getPath()));
    }

    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return panel.focusComponent();
    }

    @Override
    public @NotNull String getName() {
        return "Microscope";
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    /** Re-asks Microscope whenever the tab comes back to the front. */
    @Override
    public void selectNotify() {
        panel.refresh();
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    /**
     * Disposes the panel, and with it the embedded browser where one is in use.
     *
     * <p>A JCEF browser holds a Chromium render process of its own, one per open recording, so this
     * is what keeps a morning of opening recordings from leaving a row of them behind.
     */
    @Override
    public void dispose() {
        Disposer.dispose(panel);
    }
}
