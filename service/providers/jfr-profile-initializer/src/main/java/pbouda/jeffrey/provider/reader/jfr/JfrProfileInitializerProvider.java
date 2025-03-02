/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.ProfileInitializerProvider;
import pbouda.jeffrey.provider.reader.jfr.recording.ChunkBasedRecordingInitializer;
import pbouda.jeffrey.provider.reader.jfr.recording.RecordingInitializer;
import pbouda.jeffrey.provider.reader.jfr.recording.SingleRecordingInitializer;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.nio.file.Path;
import java.util.Map;

public class JfrProfileInitializerProvider implements ProfileInitializerProvider {

    private boolean keepSourceFiles;
    private Path tempFolder;
    private EventWriter eventWriter;
    private RecordingInitializer recordingInitializer;

    @Override
    public void initialize(Map<String, String> properties, EventWriter eventWriter) {
        String tempFolder = properties.get("temp-folder");
        if (tempFolder != null && !tempFolder.isBlank()) {
            this.tempFolder = Path.of(tempFolder);
        }

        String keepSourceFiles = properties.getOrDefault("keep-source-files", "false");
        this.keepSourceFiles = Boolean.parseBoolean(keepSourceFiles);

        boolean toolJfrEnabled = Boolean.parseBoolean(
                properties.getOrDefault("tool.jfr.enabled", "true"));

        String toolJfrPathValue = properties.get("tool.jfr.path");
        Path toolJfrPath = toolJfrPathValue != null && !toolJfrPathValue.isBlank()
                ? Path.of(toolJfrPathValue)
                : null;

        this.recordingInitializer = recordingInitializer(toolJfrEnabled, toolJfrPath);
        this.eventWriter = eventWriter;
    }

    @Override
    public ProfileInitializer newProfileInitializer() {
        return new JfrProfileInitializer(eventWriter, recordingInitializer, tempFolder, keepSourceFiles);
    }

    private static RecordingInitializer recordingInitializer(
            boolean toolJfrEnabled, Path toolJfrPath) {

        JdkJfrTool jfrTool = new JdkJfrTool(toolJfrEnabled, toolJfrPath);
        jfrTool.initialize();

        RecordingInitializer singleFileRecordingInitializer = new SingleRecordingInitializer();

        if (jfrTool.enabled()) {
            return new ChunkBasedRecordingInitializer(jfrTool, singleFileRecordingInitializer);
        } else {
            return singleFileRecordingInitializer;
        }
    }
}
