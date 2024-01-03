package pbouda.jeffrey.service;

import pbouda.jeffrey.repository.ProfileFile;

public final class Context {

    private ProfileFile selectedProfile = null;

    public void setSelectedProfile(ProfileFile profile) {
        this.selectedProfile = profile;
    }

    public ProfileFile selectedProfile() {
        return selectedProfile;
    }
}
