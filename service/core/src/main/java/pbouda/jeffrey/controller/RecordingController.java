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

package pbouda.jeffrey.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pbouda.jeffrey.controller.model.DeleteRecordingRequest;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingManager;

import java.io.IOException;
import java.nio.file.Path;

@RestController
@RequestMapping("/recordings")
public class RecordingController {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingController.class);

    private final RecordingManager recordingManager;
    private final ProfilesManager profilesManager;

    public RecordingController(
            RecordingManager recordingManager,
            ProfilesManager profilesManager) {

        this.recordingManager = recordingManager;
        this.profilesManager = profilesManager;
    }

    @GetMapping
    public JsonNode recordings() {
        return recordingManager.all();
    }

    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("files[]") MultipartFile[] files) throws IOException {
        for (MultipartFile file : files) {
            try {
                Path recordingPath = Path.of(file.getOriginalFilename());
                recordingManager.upload(recordingPath, file.getInputStream());
            } catch (Exception e) {
                LOG.error("Couldn't load recording: {}", file.getOriginalFilename(), e);
                return ResponseEntity.badRequest()
                        .body("Invalid JFR file: " + file.getOriginalFilename());
            }
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uploadAndInit")
    public ResponseEntity<String> uploadAndInit(@RequestParam("files[]") MultipartFile[] files) throws IOException {
        for (MultipartFile file : files) {
            Path recordingPath = Path.of(file.getOriginalFilename());
            try {
                recordingManager.upload(recordingPath, file.getInputStream());
            } catch (Exception e) {
                LOG.error("Couldn't load recording: {}", file.getOriginalFilename(), e);
                return ResponseEntity.badRequest()
                        .body("Invalid JFR file: " + file.getOriginalFilename());
            }
            profilesManager.createProfile(recordingPath);
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    public void deleteRecording(@RequestBody DeleteRecordingRequest request) {
        for (String filePath : request.filePaths()) {
            recordingManager.delete(Path.of(filePath));
        }
    }
}
