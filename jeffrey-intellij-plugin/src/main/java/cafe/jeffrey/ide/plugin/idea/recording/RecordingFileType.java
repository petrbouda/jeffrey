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
 * <p><b>Heap dumps are claimed too.</b> IntelliJ has its own heap-dump viewer and it is a good one,
 * so this competes with it rather than replacing it: the platform shows both editors as tabs at the
 * bottom of the window, and the developer picks. What Microscope adds is the analysis behind the
 * dump — leak suspects, the dominator tree, retained sizes — and the handoff to an agent.
 *
 * <p>Binary, because it is: saying so keeps the platform from trying to guess an encoding, load the
 * whole file as text, or offer to reformat it.
 */
public final class RecordingFileType implements FileType {

    /** The {@code fieldName} the {@code fileType} extension point instantiates from. */
    public static final RecordingFileType INSTANCE = new RecordingFileType();

    private static final String NAME = "JVM Recording";
    private static final String DESCRIPTION = "JVM recording or heap dump readable by Jeffrey Microscope";
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
