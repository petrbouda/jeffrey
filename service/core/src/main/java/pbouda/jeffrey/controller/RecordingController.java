package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.controller.model.DeleteRecordingRequest;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.AvailableRecording;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/recordings")
public class RecordingController {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingController.class);

    private final WorkingDirs workingDirs;
    private final ProfilesManager profilesManager;

    public RecordingController(WorkingDirs workingDirs, ProfilesManager profilesManager) {
        this.workingDirs = workingDirs;
        this.profilesManager = profilesManager;
    }

    @GetMapping
    public List<AvailableRecording> recordings() {
        return profilesManager.allRecordings().stream()
                .sorted(Comparator.comparing((AvailableRecording p) -> p.file().dateTime()).reversed())
                .toList();
    }

    @PostMapping("/upload")
    public void upload(@RequestParam("files[]") MultipartFile[] files) throws IOException {
        for (MultipartFile file : files) {
            try (var output = Files.newOutputStream(workingDirs.recordingsDir().resolve(file.getOriginalFilename()))) {
                file.getInputStream().transferTo(output);
            }
            LOG.info("File uploaded successfully {}", file.getOriginalFilename());
        }
    }

    @PostMapping("/delete")
    public void deleteRecording(@RequestBody DeleteRecordingRequest request) {
        for (String profileId : request.filenames()) {
            profilesManager.deleteRecording(workingDirs.recordingsDir().resolve(profileId));
        }
    }
}
