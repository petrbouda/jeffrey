package pbouda.jeffrey.manager;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.jfr.ProfilingStartTimeProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.repository.model.ProfileInfo;
import pbouda.jeffrey.repository.ProfileRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DbBasedProfilesManager implements ProfilesManager {

    private final ProfileRepository profileRepository;
    private final RecordingManager recordingManager;
    private final ProfileManager.Factory profileManagerFactory;

    public DbBasedProfilesManager(
            RecordingManager recordingManager,
            ProfileManager.Factory profileManagerFactory,
            JdbcTemplate jdbcTemplate) {
        this.recordingManager = recordingManager;
        this.profileManagerFactory = profileManagerFactory;
        this.profileRepository = new ProfileRepository(jdbcTemplate);
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return profileRepository.all().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public ProfileManager createProfile(String recordingFilename) {
        Path recordingPath = recordingManager.resolvePath(recordingFilename);

        String profileName = recordingPath.getFileName().toString()
                .replace(".jfr", "");

        var profilingStartTime = new RecordingFileIterator<>(recordingPath, new ProfilingStartTimeProcessor())
                .collect();

        ProfileInfo profileInfo = new ProfileInfo(
                profileName,
                profilingStartTime,
                recordingPath);

        profileRepository.insertProfile(profileInfo);
        return profileManagerFactory.apply(profileInfo);
    }

    @Override
    public Optional<ProfileManager> getProfile(String profileId) {
        return Optional.ofNullable(profileRepository.getProfile(profileId))
                .map(profileManagerFactory);
    }

    @Override
    public List<ProfileManager> getProfilesByRecording(String recordingFilename) {
        Path recordingPath = recordingManager.resolvePath(recordingFilename);
        return profileRepository.getProfilesByRecordingPath(recordingPath).stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public void deleteProfile(String profileId) {
        getProfile(profileId)
                .ifPresent(ProfileManager::cleanup);
    }
}
