/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.manager.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.filesystem.ProjectDirs;
import pbouda.jeffrey.tools.api.JfrTool;

import java.nio.file.Path;

public class ChunkBasedRecordingInitializer implements ProfileRecordingInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ChunkBasedRecordingInitializer.class);

    private final JfrTool jfrTool;
    private final ProfileRecordingInitializer fallbackProfileRecordingInitializer;
    private final ProjectDirs projectDirs;

    public ChunkBasedRecordingInitializer(
            ProjectDirs projectDirs,
            JfrTool jfrTool,
            ProfileRecordingInitializer fallbackProfileRecordingInitializer) {
        this.projectDirs = projectDirs;
        this.jfrTool = jfrTool;
        this.fallbackProfileRecordingInitializer = fallbackProfileRecordingInitializer;
    }

    @Override
    public void initialize(String profileId, Path sourceRecording) {
        try {
            jfrTool.disassemble(sourceRecording, projectDirs.profile(profileId).recordingsDir());
        } catch (Exception e) {
            LOG.info("Cannot disassemble using ChunkBasedRecordingInitializer, " +
                            "fallback to SingleFileRecordingInitializer: source={}, profileId={} error={}",
                    sourceRecording, profileId, e.getMessage());

            fallbackProfileRecordingInitializer.initialize(profileId, sourceRecording);
        }
    }
}
