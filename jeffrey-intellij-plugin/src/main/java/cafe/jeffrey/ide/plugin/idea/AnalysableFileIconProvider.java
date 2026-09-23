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

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * Marks the recordings and heap dumps Microscope can read with the flame icon, wherever the IDE
 * draws a file — project tree, editor tab, Search Everywhere, recent files.
 *
 * <p>Most of these formats have no icon of their own: IntelliJ knows nothing about {@code .pprof},
 * {@code .otlp} or {@code .jfr.lz4}, and Community edition knows nothing about {@code .jfr} either,
 * so a directory of profiled runs reads as a row of identical blank pages. Half a file list already
 * carrying an icon and half not would be worse than either, so the rule is the whole
 * {@link AnalysableFiles} set — which is also exactly the set that offers "Analyze in Microscope",
 * making the icon a reliable promise that the action is there.
 *
 * <p>A {@code FileIconProvider} rather than a {@code fileType} registration on purpose. Ultimate's
 * bundled profiler already claims {@code .jfr} as a file type; declaring it a second time is a
 * conflict the IDE reports to the user, and it would drag file-type behaviour — an editor, an
 * association dialog — into a plugin that only wants to draw sixteen pixels. This overrides
 * Ultimate's JFR badge where it exists, and fills the gap everywhere else.
 */
final class AnalysableFileIconProvider implements FileIconProvider {

    @Override
    public @Nullable Icon getIcon(VirtualFile file, int flags, @Nullable Project project) {
        if (!AnalysableFiles.isAnalysable(file)) {
            return null;
        }
        return JeffreyIcons.FILE;
    }
}
