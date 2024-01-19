package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.ProfileInfo;

import java.io.OutputStream;

public interface ProfileManager {

    ProfileInfo info();

    FlamegraphsManager flamegraphManager();

    HeatmapManager heatmapManager();

    OutputStream uploadPartialFlamegraph(String filename);

    OutputStream uploadHeatmap();

}
