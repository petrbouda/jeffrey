package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.TimeUtils;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.generator.timeseries.TimeseriesConfig;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.graph.GraphExporter;
import pbouda.jeffrey.graph.diff.DiffgraphGenerator;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.util.Optional;

public class DbBasedDiffgraphManager extends AbstractDbBasedGraphManager {

    private final ProfileInfo primaryProfileInfo;
    private final ProfileInfo secondaryProfileInfo;
    private final DiffgraphGenerator generator;
    private final TimeseriesGenerator timeseriesGenerator;
    private final Path primaryRecording;
    private final Path secondaryRecording;

    public DbBasedDiffgraphManager(
            ProfileInfo primaryProfileInfo,
            ProfileInfo secondaryProfileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            DiffgraphGenerator generator,
            GraphExporter graphExporter,
            TimeseriesGenerator timeseriesGenerator) {

        super(GraphType.FLAMEGRAPH, primaryProfileInfo, workingDirs, repository, graphExporter);

        this.primaryRecording = workingDirs.profileRecording(primaryProfileInfo);
        this.secondaryRecording = workingDirs.profileRecording(secondaryProfileInfo);
        this.primaryProfileInfo = primaryProfileInfo;
        this.secondaryProfileInfo = secondaryProfileInfo;
        this.generator = generator;
        this.timeseriesGenerator = timeseriesGenerator;
    }

    @Override
    public Optional<GraphContent> generateComplete(EventType eventType) {
        GraphInfo graphInfo = GraphInfo.complete(primaryProfileInfo.id(), eventType);
        var request = new DiffgraphGenerator.Request(
                primaryRecording,
                primaryProfileInfo.startedAt(),
                secondaryRecording,
                secondaryProfileInfo.startedAt(),
                eventType);

        return generate(true, graphInfo, () -> generator.generate(request));
    }

    @Override
    public ObjectNode generate(EventType eventType) {
        // Baseline is the secondary profile and comparison is the "new one" - primary
        var request = new DiffgraphGenerator.Request(
                primaryRecording,
                primaryProfileInfo.startedAt(),
                secondaryRecording,
                secondaryProfileInfo.startedAt(),
                eventType);

        return generator.generate(request);
    }

    @Override
    public ObjectNode generate(EventType eventType, TimeRange timeRange) {
        var request = new DiffgraphGenerator.Request(
                primaryRecording,
                primaryProfileInfo.startedAt(),
                secondaryRecording,
                secondaryProfileInfo.startedAt(),
                eventType,
                timeRange);

        return generator.generate(request);
    }

    @Override
    public void save(EventType eventType, TimeRange timeRange, String flamegraphName) {
        GraphInfo graphInfo = GraphInfo.custom(primaryProfileInfo.id(), eventType, flamegraphName);
        var request = new DiffgraphGenerator.Request(
                primaryRecording,
                primaryProfileInfo.startedAt(),
                secondaryRecording,
                secondaryProfileInfo.startedAt(),
                eventType,
                timeRange);

        generateAndSave(graphInfo, () -> generator.generate(request));
    }

    @Override
    public ArrayNode timeseries(EventType eventType) {
        TimeseriesConfig timeseriesConfig = TimeseriesConfig.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withSecondaryRecording(secondaryRecording)
                .withEventType(eventType)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .build();

        return timeseriesGenerator.generate(timeseriesConfig);
    }

    @Override
    public ArrayNode timeseries(EventType eventType, String searchPattern) {
        return null;
    }

    @Override
    public String generateFilename(EventType eventType) {
        return primaryProfileInfo.id() + "-diff-" + eventType.code() + "-" + TimeUtils.currentDateTime();
    }
}
