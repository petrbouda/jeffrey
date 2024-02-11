package pbouda.jeffrey.manager;

import java.util.List;
import java.util.Optional;

public interface ProfilesManager {

    List<? extends ProfileManager> allProfiles();

    ProfileManager createProfile(String recordingFilename);

    Optional<ProfileManager> getProfile(String profileId);

    List<ProfileManager> getProfilesByRecording(String recordingFilename);

    void deleteProfile(String profileId);
}
