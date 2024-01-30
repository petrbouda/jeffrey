package pbouda.jeffrey.manager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.jfr.ProfilingStartTimeProcessor;
import pbouda.jeffrey.jfrparser.jdk.RecordingFileIterator;
import pbouda.jeffrey.repository.*;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class DbBasedProfilesManager implements ProfilesManager {

    private final CommonRepository commonRepository;
    private final ProfileRepository profileRepository;
    private final FlamegraphRepository flamegraphRepository;
    private final HeatmapRepository heatmapRepository;
    private final RecordingRepository recordingRepository;
    private final TransactionTemplate transactionTemplate;
    private final WorkingDirs workingDirs;
    private final FlamegraphGenerator flamegraphGenerator;

    public DbBasedProfilesManager(
            DataSource dataSource,
            WorkingDirs workingDirs,
            FlamegraphGenerator flamegraphGenerator,
            RecordingRepository recordingRepository) {

        this.workingDirs = workingDirs;
        this.flamegraphGenerator = flamegraphGenerator;
        var jdbcTemplate = new JdbcTemplate(dataSource);

        this.transactionTemplate = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        this.commonRepository = new CommonRepository(jdbcTemplate);
        this.profileRepository = new ProfileRepository(jdbcTemplate);
        this.flamegraphRepository = new FlamegraphRepository(jdbcTemplate);
        this.heatmapRepository = new HeatmapRepository(jdbcTemplate);
        this.recordingRepository = recordingRepository;
    }

    @Override
    public List<AvailableRecording> allRecordings() {
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
    public void deleteRecording(Path jfrPath) {
        try {
            Files.delete(jfrPath);
        } catch (IOException e) {
            throw new RuntimeException("Cannot delete JFR file: " + jfrPath, e);
        }
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return profileRepository.all().stream()
                .map(profile -> new DbBasedProfileManager(
                        profile, workingDirs, flamegraphGenerator, commonRepository, flamegraphRepository, heatmapRepository))
                .toList();
    }

    @Override
    public ProfileManager createProfile(Path jfrPath) {
        String profileName = jfrPath.getFileName().toString()
                .replace(".jfr", "");

        var profilingStartTime = new RecordingFileIterator<>(jfrPath, new ProfilingStartTimeProcessor())
                .collect();

        ProfileInfo profileInfo = new ProfileInfo(
                UUID.randomUUID().toString(),
                profileName,
                Instant.now(),
                profilingStartTime,
                jfrPath);

        profileRepository.insertProfile(profileInfo);
        return new DbBasedProfileManager(profileInfo, workingDirs, flamegraphGenerator, commonRepository, flamegraphRepository, heatmapRepository);
    }

    @Override
    public Optional<ProfileManager> getProfile(String profileId) {
        return Optional.ofNullable(profileRepository.getProfile(profileId))
                .map(profile -> new DbBasedProfileManager(
                        profile, workingDirs, flamegraphGenerator, commonRepository, flamegraphRepository, heatmapRepository));
    }

    @Override
    public void deleteProfile(String profileId) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus ignored) {
                profileRepository.delete(profileId);
                flamegraphRepository.deleteByProfileId(profileId);
                heatmapRepository.deleteByProfileId(profileId);
            }
        });
    }
}
