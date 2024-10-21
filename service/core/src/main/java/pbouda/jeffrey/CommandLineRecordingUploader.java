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

package pbouda.jeffrey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import pbouda.jeffrey.filesystem.FileSystemUtils;
import pbouda.jeffrey.filesystem.HomeDirs;
import pbouda.jeffrey.manager.ProjectManager;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.repository.model.ProjectInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record CommandLineRecordingUploader(Path recordingsDir) implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineRecordingUploader.class);

    private static final String PROJECT_NAME = "Examples";

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        var homeDirs = context.getBean(HomeDirs.class);
        homeDirs.initialize();

        var projectsManager = context.getBean(ProjectsManager.class);
        ProjectManager projectManager = projectsManager.create(new ProjectInfo(PROJECT_NAME));
        Path targetDir = projectManager.dirs().recordingsDir();

        try (var fileStream = Files.walk(recordingsDir)) {
            List<Path> files = fileStream.toList();

            for (Path file : files) {
                Path relativizePath = recordingsDir.relativize(file);

                if (file.equals(recordingsDir)) {
                    // Skip the root directory
                    continue;
                }

                if (Files.isDirectory(file)) {
                    Path path = targetDir.resolve(relativizePath);
                    FileSystemUtils.createDirectories(path);
                    continue;
                }

                if (validRecordingName(relativizePath)) {
                    try {
                        projectManager.recordingsManager().upload(relativizePath, Files.newInputStream(file));
                        projectManager.profilesManager().createProfile(relativizePath, true);
                    } catch (IOException e) {
                        LOG.error("Cannot upload recording: file={}", file.getFileName().toString(), e);
                    }
                    LOG.info("Uploaded and initialized recording: {}", relativizePath);
                }
            }
        } catch (IOException e) {
            LOG.error("Cannot upload recording: error={}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static boolean validRecordingName(Path recording) {
        return !Files.isDirectory(recording)
                && recording.toString().endsWith(".jfr");
    }
}
