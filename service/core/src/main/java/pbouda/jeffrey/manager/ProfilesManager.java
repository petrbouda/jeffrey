package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.AvailableJfrFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ProfilesManager {

    List<AvailableJfrFile> allJfrFiles();

    void deleteJfrFile(Path jfrPath);

    List<? extends ProfileManager> allProfiles();

    ProfileManager createProfile(Path jfrPath);

    Optional<ProfileManager> getProfile(String profileId);

    void deleteProfile(String profileId);

}
