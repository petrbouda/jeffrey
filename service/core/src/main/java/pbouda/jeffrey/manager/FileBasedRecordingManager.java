package pbouda.jeffrey.manager;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.model.AvailableRecording;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.repository.ProfileRepository;
import pbouda.jeffrey.repository.RecordingRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FileBasedRecordingManager implements RecordingManager {

    private final WorkingDirs workingDirs;
    private final ProfileRepository profileRepository;
    private final RecordingRepository recordingRepository;

    public FileBasedRecordingManager(
            WorkingDirs workingDirs,
            ProfileRepository profileRepository,
            RecordingRepository recordingRepository) {
        this.workingDirs = workingDirs;
        this.profileRepository = profileRepository;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public List<AvailableRecording> all() {
        Set<String> profiles = profileRepository.all().stream()
                .map(ProfileInfo::name)
                .collect(Collectors.toSet());

        return recordingRepository.all().stream()
                .map(jfr -> {
                    boolean alreadyUsed = profiles.contains(jfr.filename().replace(".jfr", ""));
                    return new AvailableRecording(jfr, alreadyUsed);
                })
                .toList();
    }

    @Override
    public Path resolvePath(String filename) {
        return workingDirs.recordingsDir().resolve(filename);
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
            Path recording = workingDirs.recordingsDir().resolve(filename);
            Files.delete(recording);
        } catch (IOException e) {
            throw new RuntimeException("Cannot delete JFR file: " + recordingRepository, e);
        }
    }
}
