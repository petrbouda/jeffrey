package pbouda.jeffrey.manager;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.RecordingRepository;
import pbouda.jeffrey.repository.model.AvailableRecording;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

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
        try (var output = Files.newOutputStream(workingDirs.recordingsDir().resolve(filename))) {
            input.transferTo(output);
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
