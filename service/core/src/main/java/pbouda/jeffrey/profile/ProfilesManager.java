package pbouda.jeffrey.profile;

import java.util.Collection;

public interface ProfilesManager {

    Collection<ProfileManager> allProfiles();

    ProfileManager addProfile(String jfrFilename);

    ProfileManager getProfile(String profileId);

    void removeProfile(String profileId);

}
