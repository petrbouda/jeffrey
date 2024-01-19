package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.JfrFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ProfilesManager {

    List<JfrFile> allJfrFiles();

    List<? extends ProfileManager> allProfiles();

    ProfileManager addProfile(Path jfrPath);

    Optional<ProfileManager> getProfile(String profileId);

    void removeProfile(String profileId);

}
