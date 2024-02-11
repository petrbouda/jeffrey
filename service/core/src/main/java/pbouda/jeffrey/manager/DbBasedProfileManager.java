package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.ProfileInfo;
import pbouda.jeffrey.repository.ProfileRepository;

public class DbBasedProfileManager implements ProfileManager {

    private final ProfileInfo profileInfo;
    private final ProfileRepository profileRepository;
    private final GraphManager.FlamegraphFactory flamegraphManagerFactory;
    private final GraphManager.DiffgraphFactory diffgraphManagerFactory;
    private final HeatmapManager.Factory heatmapManagerFactory;
    private final ProfileInfoManager profileInfoManager;

    public DbBasedProfileManager(
            ProfileInfo profileInfo,
            ProfileRepository profileRepository,
            GraphManager.FlamegraphFactory flamegraphManagerFactory,
            GraphManager.DiffgraphFactory diffgraphManagerFactory,
            HeatmapManager.Factory heatmapManagerFactory,
            ProfileInfoManager profileInfoManager) {

        this.profileInfo = profileInfo;
        this.profileRepository = profileRepository;
        this.flamegraphManagerFactory = flamegraphManagerFactory;
        this.diffgraphManagerFactory = diffgraphManagerFactory;
        this.heatmapManagerFactory = heatmapManagerFactory;
        this.profileInfoManager = profileInfoManager;
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public ProfileInfoManager profileInfoManager() {
        return profileInfoManager;
    }

    @Override
    public GraphManager flamegraphManager() {
        return flamegraphManagerFactory.apply(profileInfo);
    }

    @Override
    public GraphManager diffgraphManager(ProfileManager secondaryManager) {
        return diffgraphManagerFactory.apply(profileInfo, secondaryManager.info());
    }

    @Override
    public HeatmapManager heatmapManager() {
        return heatmapManagerFactory.apply(profileInfo);
    }

    @Override
    public void cleanup() {
        profileInfoManager.cleanup();
        flamegraphManager().cleanup();
        heatmapManager().cleanup();
        profileRepository.delete(profileInfo.id());
    }
}
