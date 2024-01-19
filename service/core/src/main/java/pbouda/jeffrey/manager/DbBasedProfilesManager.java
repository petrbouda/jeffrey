package pbouda.jeffrey.manager;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.repository.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DbBasedProfilesManager implements ProfilesManager {

    private final ProfileRepository profileRepository;
    private final FlamegraphRepository flamegraphRepository;
    private final HeatmapRepository heatmapRepository;
    private final JfrRepository jfrRepository;

    public DbBasedProfilesManager(JdbcTemplate jdbcTemplate, JfrRepository jfrRepository) {
        this.profileRepository = new ProfileRepository(jdbcTemplate);
        this.flamegraphRepository = new FlamegraphRepository(jdbcTemplate);
        this.heatmapRepository = new HeatmapRepository(jdbcTemplate);
        this.jfrRepository = jfrRepository;
    }

    @Override
    public List<JfrFile> allJfrFiles() {
        return jfrRepository.all();
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return profileRepository.allProfiles().stream()
                .map(profile -> new DbBasedProfileManager(profile, flamegraphRepository, heatmapRepository))
                .toList();
    }

    @Override
    public ProfileManager addProfile(Path jfrPath) {
        String profileName = jfrPath.getFileName().toString()
                .replace(".jfr", "");

        ProfileInfo profileInfo = new ProfileInfo(
                UUID.randomUUID().toString(),
                profileName,
                Instant.now(),
                jfrPath);

        profileRepository.insertProfile(profileInfo);
        return new DbBasedProfileManager(profileInfo, flamegraphRepository, heatmapRepository);
    }

    @Override
    public Optional<ProfileManager> getProfile(String profileId) {
        return Optional.ofNullable(profileRepository.getProfile(profileId))
                .map(profile -> new DbBasedProfileManager(profile, flamegraphRepository, heatmapRepository));
    }

    @Override
    public void removeProfile(String profileId) {
        profileRepository.removeProfile(profileId);
    }
}
