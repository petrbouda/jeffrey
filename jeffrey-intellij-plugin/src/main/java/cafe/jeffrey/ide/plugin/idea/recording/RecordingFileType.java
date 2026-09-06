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

import cafe.jeffrey.ide.plugin.idea.JeffreyIcons;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * Makes a recording a file the IDE knows how to open.
 *
 * <p>This exists for one reason, and it is not the icon. A {@code FileEditorProvider} is only
 * consulted for a file the platform routes through the <b>editor</b> system, and a file with no
 * registered type is not routed there — on IntelliJ Ultimate a double-click on a {@code .jfr} instead
 * reaches {@code ImportProfilerResultAction}, which loads the recording into the bundled profiler's
 * tool window and never opens an editor at all. Declaring the type is what puts the recording panel
 * back in the path.
 *
 * <p><b>Heap dumps are deliberately not claimed.</b> IntelliJ's own heap-dump viewer is a good tool
 * and owns {@code .hprof} already; taking that extension would be a fight worth nothing. Those files
 * still carry the flame icon through {@link cafe.jeffrey.ide.plugin.idea.AnalysableFileIconProvider}
 * and still offer "Analyze in Microscope" — they simply are not what a double-click opens.
 *
 * <p>Binary, because it is: saying so keeps the platform from trying to guess an encoding, load the
 * whole file as text, or offer to reformat it.
 */
public final class RecordingFileType implements FileType {

    /** The {@code fieldName} the {@code fileType} extension point instantiates from. */
    public static final RecordingFileType INSTANCE = new RecordingFileType();

    private static final String NAME = "JVM Recording";
    private static final String DESCRIPTION = "JVM recording readable by Jeffrey Microscope";
    private static final String DEFAULT_EXTENSION = "jfr";

    private RecordingFileType() {
    }

    @Override
    public @NotNull String getName() {
        return NAME;
    }

    @Override
    public @NotNull String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return DEFAULT_EXTENSION;
    }

    @Override
    public Icon getIcon() {
        return JeffreyIcons.FILE;
    }

    @Override
    public boolean isBinary() {
        return true;
    }
}
