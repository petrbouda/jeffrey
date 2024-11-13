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
import pbouda.jeffrey.common.JfrFileUtils;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.ProjectDirs;
import pbouda.jeffrey.common.filesystem.RecordingUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

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
        return upload(relativePath, targetPath -> FileSystemUtils.copyStream(targetPath, stream));
    }

    @Override
    public Path mergeAndUpload(Path relativePath, List<Path> paths) {
        return upload(relativePath, targetPath -> FileSystemUtils.concatFiles(targetPath, paths));
    }

    private Path upload(Path relativePath, Function<Path, Path> uploader) {
        Path targetPath = projectDirs.recordingsDir().resolve(relativePath);
        FileSystemUtils.createDirectories(targetPath.getParent());
        Path uploaded = uploader.apply(targetPath);
        if (!JfrFileUtils.isJfrFileReadable(uploaded)) {
            LOG.warn("The uploaded file is not a valid JFR file: {}", uploaded);
            FileSystemUtils.delete(uploaded);
            throw new IllegalArgumentException("The uploaded file is not a valid JFR file: " + uploaded);
        }
        return uploaded;
    }

    @Override
    public void delete(Path file) {
        Path recording = projectDirs.recordingsDir().resolve(file);
        FileSystemUtils.delete(recording);
    }
}
