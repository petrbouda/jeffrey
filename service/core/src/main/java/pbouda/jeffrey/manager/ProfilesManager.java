package pbouda.jeffrey.manager;

import java.util.List;
import java.util.Optional;

public interface ProfilesManager {

    List<? extends ProfileManager> allProfiles();

    ProfileManager createProfile(String recordingFilename, boolean postCreateActions);

    Optional<ProfileManager> getProfile(String profileId);

    void deleteProfile(String profileId);

    default ProfileManager createProfile(String recordingFilename) {
        return createProfile(recordingFilename, false);
    }
}
