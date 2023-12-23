package pbouda.jeffrey.service;

import pbouda.jeffrey.repository.Profile;

public final class Context {

    private Profile selectedProfile = null;

    public void setSelectedProfile(Profile profile) {
        this.selectedProfile = profile;
    }

    public Profile selectedProfile() {
        return selectedProfile;
    }
}
