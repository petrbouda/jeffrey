package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.graph.GraphExporter;
import pbouda.jeffrey.graph.diff.DiffgraphGenerator;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.util.Optional;

public class DbBasedDiffgraphManager extends AbstractDbBasedGraphManager {

    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final DiffgraphGenerator generator;
    private final TimeseriesGenerator timeseriesGenerator;

    public DbBasedDiffgraphManager(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            DiffgraphGenerator generator,
            GraphExporter graphExporter,
            TimeseriesGenerator timeseriesGenerator) {

        super(primaryProfileInfo, workingDirs, repository, graphExporter);

        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.generator = generator;
        this.timeseriesGenerator = timeseriesGenerator;
    }

    @Override
    public Optional<GraphContent> generateComplete(EventType eventType) {
        GraphInfo graphInfo = GraphInfo.complete(primaryProfileInfo.id(), eventType);
        var request = new DiffgraphGenerator.Request(
                primaryProfileInfo.recordingPath(), secondaryProfileInfo.recordingPath(), eventType, null);

        return generate(true, graphInfo, () -> generator.generate(request));
    }

    @Override
    public ObjectNode generate(EventType eventType) {
        // Baseline is the secondary profile and comparison is the "new one" - primary
        var request = new DiffgraphGenerator.Request(
                secondaryProfileInfo.recordingPath(), primaryProfileInfo.recordingPath(), eventType);

        return generator.generate(request);
    }

    @Override
    public ObjectNode generate(EventType eventType, TimeRange timeRange) {
        // Baseline is the secondary profile and comparison is the "new one" - primary
        var request = new DiffgraphGenerator.Request(
                secondaryProfileInfo.recordingPath(), primaryProfileInfo.recordingPath(), eventType, timeRange);

        return generator.generate(request);
    }

    @Override
    public ArrayNode timeseries(EventType eventType) {
        TimeseriesConfig timeseriesConfig = TimeseriesConfig.differentialBuilder()
                .withPrimaryRecording(primaryProfileInfo.recordingPath())
                .withSecondaryRecording(secondaryProfileInfo.recordingPath())
                .withEventType(eventType)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .build();

        return timeseriesGenerator.generate(timeseriesConfig);
    }

    @Override
    public Optional<GraphContent> generateCustom(EventType eventType, TimeRange timeRange, String name) {
        GraphInfo graphInfo = GraphInfo.custom(primaryProfileInfo.id(), eventType, name);
        var request = new DiffgraphGenerator.Request(
                primaryProfileInfo.recordingPath(), secondaryProfileInfo.recordingPath(), eventType, timeRange);

        return generate(false, graphInfo, () -> generator.generate(request));
    }
}
