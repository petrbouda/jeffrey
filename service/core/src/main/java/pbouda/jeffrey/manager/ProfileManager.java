package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.ProfileInfo;

public interface ProfileManager {

    ProfileInfo info();

    byte[] information();

    FlamegraphsManager flamegraphManager();

    HeatmapManager heatmapManager();

}
