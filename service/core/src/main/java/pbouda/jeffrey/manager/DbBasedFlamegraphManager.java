package pbouda.jeffrey.manager;

import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.graph.GraphExporter;
import pbouda.jeffrey.graph.flame.FlamegraphGenerator;
import pbouda.jeffrey.repository.GraphContent;
import pbouda.jeffrey.repository.GraphInfo;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.util.Optional;

public class DbBasedFlamegraphManager extends AbstractDbBasedGraphManager {

    private final ProfileInfo profileInfo;
    private final FlamegraphGenerator generator;

    public DbBasedFlamegraphManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            FlamegraphGenerator generator,
            GraphExporter graphExporter) {

        super(profileInfo, workingDirs, repository, graphExporter);

        this.profileInfo = profileInfo;
        this.generator = generator;
    }

    @Override
    public Optional<GraphContent> generateComplete(EventType eventType) {
        GraphInfo graphInfo = GraphInfo.complete(profileInfo.id(), eventType);
        return generate(graphInfo, () -> generator.generate(profileInfo.recordingPath(), eventType));
    }

    @Override
    public Optional<GraphContent> generateCustom(EventType eventType, TimeRange timeRange, String name) {
        GraphInfo graphInfo = GraphInfo.custom(profileInfo.id(), eventType, name);
        return generate(graphInfo, () -> generator.generate(profileInfo.recordingPath(), eventType, timeRange));
    }
}
