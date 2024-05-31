package pbouda.jeffrey.manager;

import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Type;
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
    public byte[] contentByName(String heatmapName, Type eventType, boolean collectWeight) {
        return generate(eventType, collectWeight);

//        return heatmapRepository.contentByName(profileInfo.id(), heatmapName)
//                .orElseGet(() -> {
//                    byte[] content = generate(eventType, collectWeight);
//                    heatmapRepository.insert(new HeatmapInfo(profileInfo.id(), heatmapName), content);
//                    return content;
//                });
    }

    private byte[] generate(Type eventType, boolean collectWeight) {
        HeatmapConfig heatmapConfig = HeatmapConfig.builder()
                .withRecording(workingDirs.profileRecording(profileInfo))
                .withEventType(eventType)
                .withProfilingStart(profileInfo.startedAt())
                .withHeatmapStart(Duration.ZERO)
                .withDuration(Duration.ofMinutes(5))
                .withCollectWeight(collectWeight)
                .build();

        return heatmapGenerator.generate(heatmapConfig);
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
