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

import pbouda.jeffrey.common.filesystem.ProjectDirs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SingleFileRecordingInitializer implements ProfileRecordingInitializer {

    private final ProjectDirs projectDirs;

    public SingleFileRecordingInitializer(ProjectDirs projectDirs) {
        this.projectDirs = projectDirs;
    }

    @Override
    public void initialize(String profileId, Path sourceRecording) {
        Path target = projectDirs.profile(profileId).recordingsDir()
                .resolve(sourceRecording.getFileName());
        try {
            Files.copy(sourceRecording, target);
        } catch (IOException e) {
            throw new RuntimeException("Cannot copy a recording: source=" + sourceRecording + " target=" + target, e);
        }
    }
}
