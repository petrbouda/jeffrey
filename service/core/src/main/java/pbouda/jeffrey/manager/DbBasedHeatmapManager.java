package pbouda.jeffrey.manager;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.heatmap.HeatmapConfig;
import pbouda.jeffrey.generator.heatmap.api.HeatmapGenerator;
import pbouda.jeffrey.repository.HeatmapRepository;
import pbouda.jeffrey.repository.model.HeatmapInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.time.Duration;
import java.util.List;

public class DbBasedHeatmapManager implements HeatmapManager {

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final HeatmapRepository heatmapRepository;
    private final HeatmapGenerator heatmapGenerator;

    public DbBasedHeatmapManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            HeatmapRepository heatmapRepository,
            HeatmapGenerator heatmapGenerator) {

        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.heatmapRepository = heatmapRepository;
        this.heatmapGenerator = heatmapGenerator;
    }

    @Override
    public List<HeatmapInfo> all() {
        return heatmapRepository.all(profileInfo.id());
    }

    @Override
    public byte[] contentByName(String heatmapName, EventType eventType) {
        return heatmapRepository.contentByName(profileInfo.id(), heatmapName)
                .orElseGet(() -> generate(heatmapName, eventType));
    }

    private byte[] generate(String heatmapName, EventType eventType) {
        HeatmapConfig heatmapConfig = HeatmapConfig.builder()
                .withRecording(workingDirs.profileRecording(profileInfo))
                .withEventType(eventType)
                .withProfilingStart(profileInfo.startedAt())
                .withHeatmapStart(Duration.ZERO)
                .withDuration(Duration.ofMinutes(5))
                .build();

        byte[] result = heatmapGenerator.generate(heatmapConfig);
        heatmapRepository.insert(new HeatmapInfo(profileInfo.id(), heatmapName), result);
        return result;
    }

    @Override
    public void delete(String heatmapId) {
        heatmapRepository.delete(profileInfo.id(), heatmapId);
    }

    @Override
    public void cleanup() {
        heatmapRepository.deleteByProfileId(profileInfo.id());
    }
}
