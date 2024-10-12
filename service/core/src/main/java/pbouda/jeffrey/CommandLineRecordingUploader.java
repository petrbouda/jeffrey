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
import pbouda.jeffrey.filesystem.HomeDirs;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record CommandLineRecordingUploader(Path recordingsDir) implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineRecordingUploader.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        var recordingManager = context.getBean(RecordingManager.class);
        var profilesManager = context.getBean(ProfilesManager.class);
        var workingDirs = context.getBean(HomeDirs.class);
        workingDirs.initialize();

        try (var fileStream = Files.list(recordingsDir)) {
            fileStream.forEach(recording -> {
                if (!validRecordingName(recording)) {
                    return;
                }

                Path relativizePath = recordingsDir.relativize(recording);

                try {
                    recordingManager.upload(relativizePath, Files.newInputStream(recording));
                    profilesManager.createProfile(relativizePath, true);
                } catch (Exception e) {
                    LOG.error("Cannot upload recording: file={}", recording.getFileName().toString(), e);
                }
                LOG.info("Uploaded and initialized recording: {}", relativizePath);
            });
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
