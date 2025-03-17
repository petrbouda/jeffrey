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

import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.model.EventFieldsSetting;
import pbouda.jeffrey.provider.api.EventWriter;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.ProfileInitializerProvider;
import pbouda.jeffrey.provider.reader.jfr.recording.ChunkBasedRecordingInitializer;
import pbouda.jeffrey.provider.reader.jfr.recording.RecordingInitializer;
import pbouda.jeffrey.provider.reader.jfr.recording.SingleRecordingInitializer;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

public class JfrProfileInitializerProvider implements ProfileInitializerProvider {

    private boolean keepSourceFiles;
    private Path tempFolder;
    private Supplier<EventWriter> eventWriterSupplier;
    private RecordingInitializer recordingInitializer;
    private EventFieldsSetting eventFieldsSetting;

    @Override
    public void initialize(Map<String, String> properties, Supplier<EventWriter> eventWriterSupplier) {
        String tempFolder = properties.get("temp-folder");
        if (tempFolder != null && !tempFolder.isBlank()) {
            this.tempFolder = Path.of(tempFolder);
        }

        this.keepSourceFiles = Config.parseBoolean(properties, "keep-source-files", false);
        boolean toolJfrEnabled = Config.parseBoolean(properties, "tool.jfr.enabled", true);

        String eventFieldsParsing = Config.parseString(properties, "event-fields-setting", "ALL");
        this.eventFieldsSetting = EventFieldsSetting.valueOf(eventFieldsParsing.toUpperCase());

        String toolJfrPathValue = properties.get("tool.jfr.path");
        Path toolJfrPath = toolJfrPathValue != null && !toolJfrPathValue.isBlank()
                ? Path.of(toolJfrPathValue)
                : null;

        this.recordingInitializer = recordingInitializer(toolJfrEnabled, toolJfrPath);
        this.eventWriterSupplier = eventWriterSupplier;
    }

    @Override
    public ProfileInitializer newProfileInitializer() {
        return new JfrProfileInitializer(
                eventWriterSupplier.get(),
                recordingInitializer,
                tempFolder,
                keepSourceFiles,
                eventFieldsSetting);
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
