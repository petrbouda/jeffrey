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

    /** The panel behind this tab, so an action outside it can attach a baseline to a comparison. */
    RecordingPanel panel() {
        return panel;
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
