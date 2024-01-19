package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.HeatmapInfo;
import pbouda.jeffrey.repository.HeatmapRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.util.List;
import java.util.Optional;

public class DbBasedHeatmapManager implements HeatmapManager {

    private final ProfileInfo profileInfo;
    private final HeatmapRepository heatmapRepository;

    public DbBasedHeatmapManager(ProfileInfo profileInfo, HeatmapRepository heatmapRepository) {
        this.profileInfo = profileInfo;
        this.heatmapRepository = heatmapRepository;
    }

    @Override
    public List<HeatmapInfo> all() {
        return heatmapRepository.all(profileInfo.id());
    }

    @Override
    public Optional<byte[]> content(String heatmapId) {
        return heatmapRepository.content(profileInfo.id(), heatmapId);
    }

    @Override
    public Optional<byte[]> contentByName(String heatmapName) {
        return heatmapRepository.contentByName(profileInfo.id(), heatmapName);
    }

    @Override
    public void upload(HeatmapInfo heatmapInfo, byte[] content) {
        heatmapRepository.insert(heatmapInfo, content);
    }

    @Override
    public void delete(String heatmapId) {
        heatmapRepository.delete(profileInfo.id(), heatmapId);
    }
}
