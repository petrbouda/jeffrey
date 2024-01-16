package pbouda.jeffrey.profile;

import pbouda.jeffrey.FileUtils;
import pbouda.jeffrey.Naming;
import pbouda.jeffrey.exception.NotFoundException;
import pbouda.jeffrey.info.InfoProvider;
import pbouda.jeffrey.info.ProfileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

public class FileBasedProfilesManager implements ProfilesManager {

    private final InfoProvider infoProvider;

    public FileBasedProfilesManager(InfoProvider infoProvider) {
        this.infoProvider = infoProvider;
    }

    @Override
    public List<? extends ProfileManager> allProfiles() {
        return infoProvider.profiles().stream()
                .map(FileBasedProfileManager::new)
                .toList();
    }

    @Override
    public ProfileManager addProfile(Path jfrPath) {
        ProfileInfo profileInfo = infoProvider.generateProfile(jfrPath);
        Path targetJfr = profileInfo.dataDir().resolve(Naming.JFR_PROFILE_NAME);
        try {
            Files.copy(jfrPath, targetJfr, ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot move JFR file to the profile folder: jfr_file=" + jfrPath + " target=" + targetJfr, e);
        }
        return new FileBasedProfileManager(profileInfo);
    }

    @Override
    public ProfileManager getProfile(String profileId) {
        return infoProvider.getProfile(profileId)
                .map(FileBasedProfileManager::new)
                .orElseThrow(profileNotFound(profileId));
    }

    @Override
    public void removeProfile(String profileId) {
        ProfileInfo profileInfo = infoProvider.removeProfile(profileId)
                .orElseThrow(profileNotFound(profileId));

        FileUtils.removeDirectory(profileInfo.dataDir());
    }

    private static Supplier<NotFoundException> profileNotFound(String profileId) {
        return () -> new NotFoundException("No profile found with ID: " + profileId);
    }
}
