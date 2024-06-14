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
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.jfr.ReadOneEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.repository.RecordingRepository;
import pbouda.jeffrey.repository.model.AvailableRecording;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileBasedRecordingManager implements RecordingManager {

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedRecordingManager.class);

    private final WorkingDirs workingDirs;
    private final RecordingRepository recordingRepository;

    public FileBasedRecordingManager(
            WorkingDirs workingDirs,
            RecordingRepository recordingRepository) {
        this.workingDirs = workingDirs;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public List<AvailableRecording> all() {
        List<String> profileNames = workingDirs.retrieveAllProfiles().stream()
                .map(ProfileInfo::name)
                .toList();

        return recordingRepository.all().stream()
                .map(jfr -> {
                    String profileName = jfr.filename().replace(".jfr", "");
                    boolean alreadyUsed = profileNames.contains(profileName);
                    return new AvailableRecording(jfr, alreadyUsed);
                })
                .toList();
    }

    @Override
    public void upload(String filename, InputStream input) throws IOException {
        Path recordingPath = workingDirs.recordingsDir().resolve(filename);
        try (var output = Files.newOutputStream(recordingPath)) {
            input.transferTo(output);
        }

        try {
            new RecordingFileIterator<>(recordingPath, new ReadOneEventProcessor())
                    .collect();
        } catch (Exception ex) {
            Files.deleteIfExists(recordingPath);
            throw ex;
        }
    }

    @Override
    public void delete(String filename) {
        try {
            Files.delete(workingDirs.recordingsDir().resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("Cannot delete JFR file: " + recordingRepository, e);
        }
    }
}
