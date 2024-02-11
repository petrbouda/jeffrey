package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.ProfileInfo;

import java.util.function.Function;

public interface ProfileManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ProfileManager> {
    }

    ProfileInfo info();

    ProfileInfoManager profileInfoManager();

    GraphManager flamegraphManager();

    GraphManager diffgraphManager(ProfileManager secondaryManager);

    HeatmapManager heatmapManager();

    void cleanup();
}
