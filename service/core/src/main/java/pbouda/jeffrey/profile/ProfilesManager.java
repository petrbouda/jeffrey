package pbouda.jeffrey.profile;

import java.nio.file.Path;
import java.util.List;

public interface ProfilesManager {

    List<? extends ProfileManager> allProfiles();

    ProfileManager addProfile(Path jfrPath);

    ProfileManager getProfile(String profileId);

    void removeProfile(String profileId);

}
