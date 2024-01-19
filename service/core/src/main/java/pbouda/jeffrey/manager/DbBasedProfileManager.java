package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.FlamegraphRepository;
import pbouda.jeffrey.repository.HeatmapInfo;
import pbouda.jeffrey.repository.HeatmapRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.io.OutputStream;

public class DbBasedProfileManager implements ProfileManager {

    private final ProfileInfo profileInfo;
    private final FlamegraphRepository flamegraphRepository;
    private final HeatmapRepository heatmapRepository;

    public DbBasedProfileManager(
            ProfileInfo profileInfo,
            FlamegraphRepository flamegraphRepository,
            HeatmapRepository heatmapRepository) {

        this.profileInfo = profileInfo;
        this.flamegraphRepository = flamegraphRepository;
        this.heatmapRepository = heatmapRepository;
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public FlamegraphsManager flamegraphManager() {
        return new DbBasedFlamegraphsManager(profileInfo, flamegraphRepository);
    }

    @Override
    public HeatmapManager heatmapManager() {
        return new DbBasedHeatmapManager(profileInfo, heatmapRepository);
    }

    @Override
    public OutputStream uploadPartialFlamegraph(String filename) {
        return null;
    }

    @Override
    public OutputStream uploadHeatmap() {
        return null;
    }
}
