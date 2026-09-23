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

import cafe.jeffrey.ide.plugin.idea.AnalysableFiles;
import cafe.jeffrey.ide.plugin.idea.settings.JeffreySettings;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Gives every recording Microscope can read an editor tab of its own.
 *
 * <p>{@link FileEditorPolicy#PLACE_BEFORE_DEFAULT_EDITOR} rather than {@code HIDE_DEFAULT_EDITOR},
 * deliberately. Ultimate's bundled profiler opens {@code .jfr} in a viewer of its own, and hiding the
 * default would decide on the developer's behalf that Microscope wins. Placing this one first makes
 * it what opens, and leaves the IDE's own tab a click away at the bottom of the editor.
 *
 * <p>{@link DumbAware} because nothing here needs indexes — the panel talks to Microscope over HTTP
 * and reads no PSI, so a recording opened during indexing should show its panel like any other time.
 */
final class RecordingFileEditorProvider implements FileEditorProvider, DumbAware {

    /**
     * Stable across versions: IntelliJ stores it per file to reopen the right editor, so renaming it
     * would silently drop every remembered tab back to the binary viewer.
     */
    private static final String EDITOR_TYPE_ID = "jeffrey-microscope-recording";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return JeffreySettings.getInstance().isEnabled() && AnalysableFiles.isAnalysable(file);
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new RecordingFileEditor(project, file);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR;
    }
}
