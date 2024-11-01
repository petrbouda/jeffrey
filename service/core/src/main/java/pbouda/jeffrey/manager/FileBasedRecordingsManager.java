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

package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.filesystem.RecordingUtils;
import pbouda.jeffrey.jfr.ReadOneEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileBasedRecordingsManager implements RecordingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedRecordingsManager.class);

    private final ProjectDirs projectDirs;

    public FileBasedRecordingsManager(ProjectDirs workingDirs) {
        this.projectDirs = workingDirs;
    }

    @Override
    public List<pbouda.jeffrey.common.Recording> all() {
        return RecordingUtils.all(projectDirs.recordingsDir());
    }

    @Override
    public Path upload(Path relativePath, InputStream stream) {
        Path targetPath = projectDirs.recordingsDir().resolve(relativePath);
        FileSystemUtils.createDirectories(targetPath.getParent());
        FileSystemUtils.upload(targetPath, stream);

        try {
            JdkRecordingIterators.singleAndCollectIdentical(targetPath, new ReadOneEventProcessor());
        } catch (Exception ex) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException e) {
                LOG.error("Failed to delete the recording: {}", targetPath, e);
            }
            throw ex;
        }

        return targetPath;
    }

    @Override
    public void delete(Path file) {
        Path recording = projectDirs.recordingsDir().resolve(file);
        FileSystemUtils.delete(recording);
    }
}
