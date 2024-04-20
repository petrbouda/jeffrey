package pbouda.jeffrey.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.FlywayMigration;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.jfr.ProfilingStartTimeProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DbBasedProfilesManager implements ProfilesManager {

    private static final Logger LOG = LoggerFactory.getLogger(DbBasedProfilesManager.class);

    private final WorkingDirs workingDirs;
    private final ProfileManager.Factory profileManagerFactory;

    public DbBasedProfilesManager(ProfileManager.Factory profileManagerFactory, WorkingDirs workingDirs) {
        this.profileManagerFactory = profileManagerFactory;
        this.workingDirs = workingDirs;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return workingDirs.retrieveAllProfiles().stream()
                .map(profileManagerFactory)
                .toList();
    }

    @Override
    public ProfileManager createProfile(String recordingFilename) {
        String profileId = UUID.randomUUID().toString();

        Path profileDir = workingDirs.createProfileHierarchy(profileId);
        LOG.info("Profile's directory created: {}", profileDir);

        Path recordingPath = workingDirs.copyRecording(profileId, recordingFilename);
        LOG.info("Recording copied to the profile's directory: {}", recordingPath);

        // Name derived from the recording
        // It can be a part of Profile Creation in the future.
        String profileName = recordingFilename.replace(".jfr", "");

        var profilingStartTime = new RecordingFileIterator<>(recordingPath, new ProfilingStartTimeProcessor())
                .collect();

        ProfileInfo profileInfo = new ProfileInfo(profileId, profileName, Instant.now(), profilingStartTime);

        Path profileInfoPath = workingDirs.createProfileInfo(profileInfo);
        LOG.info("New profile's info generated: profile_info={}", profileInfoPath);

        FlywayMigration.migrate(workingDirs, profileInfo);
        LOG.info("Schema migrated to the new database file: {}", workingDirs.profileDbFile(profileInfo));

        return profileManagerFactory.apply(profileInfo);
    }

    @Override
    public Optional<ProfileManager> getProfile(String profileId) {
        return Optional.ofNullable(workingDirs.retrieveProfileInfo(profileId))
                .map(profileManagerFactory);
    }

    @Override
    public void deleteProfile(String profileId) {
        getProfile(profileId)
                .ifPresent(ProfileManager::cleanup);
    }
}
