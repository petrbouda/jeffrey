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

import com.fasterxml.jackson.databind.JsonNode;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.treetable.RecordingData;
import pbouda.jeffrey.common.treetable.Tree;
import pbouda.jeffrey.common.treetable.TreeData;
import pbouda.jeffrey.jfr.ReadOneEventProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingIterators;
import pbouda.jeffrey.repository.RecordingRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.repository.model.Recording;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

public class FileBasedRecordingManager implements RecordingManager {

    private final WorkingDirs workingDirs;
    private final RecordingRepository recordingRepository;

    public FileBasedRecordingManager(
            WorkingDirs workingDirs,
            RecordingRepository recordingRepository) {
        this.workingDirs = workingDirs;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public JsonNode all() {
        List<String> profileNames = workingDirs.retrieveAllProfiles().stream()
                .map(ProfileInfo::originalRecordingName)
                .toList();

        Tree tree = new Tree();
        for (Recording recording : recordingRepository.all()) {
            TreeData data = new RecordingData(
                    generateCategories(recording),
                    recording.relativePath().getFileName().toString(),
                    recording.dateTime().toString(),
                    recording.sizeInBytes(),
                    isAlreadyUsed(profileNames, recording));

            tree.add(data);
        }

        return Json.mapper().valueToTree(tree.getRoot().getChildren());
    }

    private static List<String> generateCategories(Recording recording) {
        Path parent = recording.relativePath().getParent();
        if (parent == null || parent.getNameCount() == 0) {
            return List.of();
        }

        return StreamSupport.stream(parent.spliterator(), false)
                .map(Path::toString)
                .toList();
    }

    private static boolean isAlreadyUsed(List<String> profileRecordingPaths, Recording recording) {
        return profileRecordingPaths.contains(recording.relativePath().toString());
    }

    @Override
    public void upload(Path filename, InputStream input) throws IOException {
        Path recordingPath = workingDirs.recordingsDir().resolve(filename);
        final BufferedInputStream buffered = new BufferedInputStream(input);
        try (var output = Files.newOutputStream(recordingPath)) {
            if (isGzipped(buffered)) {
                try (GZIPInputStream gzipInput = new GZIPInputStream(buffered)) {
                    gzipInput.transferTo(output);
                }
            } else {
                buffered.transferTo(output);
            }
        }

        try {
            RecordingIterators.singleAndCollectIdentical(recordingPath, new ReadOneEventProcessor());
        } catch (Exception ex) {
            Files.deleteIfExists(recordingPath);
            throw ex;
        }
    }

    @Override
    public void delete(Path filename) {
        try {
            Files.delete(workingDirs.recordingsDir().resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("Cannot delete JFR file: " + recordingRepository, e);
        }
    }

    private boolean isGzipped(BufferedInputStream input) throws IOException {
        input.mark(2);
        int magic = (input.read() & 0xff) | ((input.read() << 8) & 0xff00);
        input.reset();
        return magic == GZIPInputStream.GZIP_MAGIC;
    }
}
