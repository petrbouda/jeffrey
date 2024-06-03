package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pbouda.jeffrey.controller.model.DeleteRecordingRequest;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingManager;
import pbouda.jeffrey.repository.model.AvailableRecording;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

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
    public List<AvailableRecording> recordings() {
        return recordingManager.all().stream()
                .sorted(Comparator.comparing((AvailableRecording p) -> p.file().dateTime()).reversed())
                .toList();
    }

    @PostMapping("/upload")
    public void upload(@RequestParam("files[]") MultipartFile[] files) throws IOException {
        for (MultipartFile file : files) {
            recordingManager.upload(file.getOriginalFilename(), file.getInputStream());
            LOG.info("File uploaded successfully {}", file.getOriginalFilename());
        }
    }

    @PostMapping("/uploadAndInit")
    public void uploadAndInit(@RequestParam("files[]") MultipartFile[] files) throws IOException {
        for (MultipartFile file : files) {
            recordingManager.upload(file.getOriginalFilename(), file.getInputStream());
            profilesManager.createProfile(file.getOriginalFilename());
            LOG.info("File uploaded and a new profile created successfully {}", file.getOriginalFilename());
        }
    }

    @PostMapping("/delete")
    public void deleteRecording(@RequestBody DeleteRecordingRequest request) {
        for (String filename : request.filenames()) {
            recordingManager.delete(filename);
        }
    }
}
