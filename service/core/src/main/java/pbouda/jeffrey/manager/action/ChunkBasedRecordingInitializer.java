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

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.tools.api.JfrTool;

import java.nio.file.Path;

public class ChunkBasedRecordingInitializer implements ProfileRecordingInitializer {

    private final WorkingDirs workingDirs;
    private final JfrTool jfrTool;

    public ChunkBasedRecordingInitializer(WorkingDirs workingDirs, JfrTool jfrTool) {
        this.workingDirs = workingDirs;
        this.jfrTool = jfrTool;
    }

    @Override
    public void initialize(String profileId, Path sourceRecording) {
        jfrTool.disassemble(sourceRecording, workingDirs.profileRecordingDir(profileId));
    }
}
