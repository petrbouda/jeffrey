package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.jfr.configuration.ProfileInformationProvider;
import pbouda.jeffrey.repository.CommonRepository;
import pbouda.jeffrey.repository.FlamegraphRepository;
import pbouda.jeffrey.repository.HeatmapRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.nio.charset.Charset;
import java.util.Optional;

public class DbBasedProfileManager implements ProfileManager {

    private final WorkingDirs workingDirs;
    private final FlamegraphGenerator flamegraphGenerator;
    private final ProfileInfo profileInfo;
    private final CommonRepository commonRepository;
    private final FlamegraphRepository flamegraphRepository;
    private final HeatmapRepository heatmapRepository;
    private final ProfileInformationProvider infoProvider;

    public DbBasedProfileManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            FlamegraphGenerator flamegraphGenerator,
            CommonRepository commonRepository,
            FlamegraphRepository flamegraphRepository,
            HeatmapRepository heatmapRepository) {

        this.workingDirs = workingDirs;
        this.flamegraphGenerator = flamegraphGenerator;
        this.profileInfo = profileInfo;
        this.commonRepository = commonRepository;
        this.flamegraphRepository = flamegraphRepository;
        this.heatmapRepository = heatmapRepository;
        this.infoProvider = new ProfileInformationProvider(profileInfo.recordingPath());
    }

    @Override
    public ProfileInfo info() {
        return profileInfo;
    }

    @Override
    public byte[] information() {
        Optional<byte[]> content = commonRepository.selectInformation(profileInfo.id());
        if (content.isPresent()) {
            return content.get();
        } else {
            ObjectNode jsonContent = infoProvider.get();
            commonRepository.insertInformation(profileInfo.id(), jsonContent);
            return jsonContent.toString().getBytes(Charset.defaultCharset());
        }
    }

    @Override
    public FlamegraphsManager flamegraphManager() {
        return new DbBasedFlamegraphsManager(profileInfo, workingDirs, flamegraphRepository, flamegraphGenerator);
    }

    @Override
    public HeatmapManager heatmapManager() {
        return new DbBasedHeatmapManager(profileInfo, heatmapRepository);
    }
}
