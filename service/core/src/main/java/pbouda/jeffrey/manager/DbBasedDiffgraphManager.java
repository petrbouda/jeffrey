package pbouda.jeffrey.manager;

import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.graph.GraphExporter;
import pbouda.jeffrey.graph.diff.DiffgraphGenerator;
import pbouda.jeffrey.repository.GraphContent;
import pbouda.jeffrey.repository.GraphInfo;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.util.Optional;

public class DbBasedDiffgraphManager extends AbstractDbBasedGraphManager {

    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final DiffgraphGenerator generator;

    public DbBasedDiffgraphManager(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            DiffgraphGenerator generator,
            GraphExporter graphExporter) {

        super(primaryProfileInfo, workingDirs, repository, graphExporter);

        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.generator = generator;
    }

    @Override
    public Optional<GraphContent> generateComplete(EventType eventType) {
        GraphInfo graphInfo = GraphInfo.complete(primaryProfileInfo.id(), eventType);
        var request = new DiffgraphGenerator.Request(
                primaryProfileInfo.recordingPath(), secondaryProfileInfo.recordingPath(), eventType, null);

        return generate(true, graphInfo, () -> generator.generate(request));
    }

    @Override
    public Optional<GraphContent> generateCustom(EventType eventType, TimeRange timeRange, String name) {
        GraphInfo graphInfo = GraphInfo.custom(primaryProfileInfo.id(), eventType, name);
        var request = new DiffgraphGenerator.Request(
                primaryProfileInfo.recordingPath(), secondaryProfileInfo.recordingPath(), eventType, timeRange);

        return generate(false, graphInfo, () -> generator.generate(request));
    }
}
