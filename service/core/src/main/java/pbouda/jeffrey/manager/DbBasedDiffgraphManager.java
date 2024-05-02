package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.TimeUtils;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.Config;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.common.TimeRange;
import pbouda.jeffrey.generator.flamegraph.GraphExporter;
import pbouda.jeffrey.generator.flamegraph.diff.DiffgraphGenerator;
import pbouda.jeffrey.generator.timeseries.api.TimeseriesGenerator;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;

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
    public ObjectNode generate(EventType eventType, boolean threadMode) {
        // Baseline is the secondary profile and comparison is the "new one" - primary
        Config config = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryRecording(secondaryRecording)
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withEventType(eventType)
                .withThreadMode(threadMode)
                .build();

        return generator.generate(config);
    }

    @Override
    public ObjectNode generate(EventType eventType, TimeRangeRequest timeRange, boolean threadMode) {
        Config config = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryRecording(secondaryRecording)
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withEventType(eventType)
                .withThreadMode(threadMode)
                .withTimeRange(TimeRange.create(timeRange.start(), timeRange.end(), timeRange.absoluteTime()))
                .build();

        return generator.generate(config);
    }

    @Override
    public void save(EventType eventType, TimeRangeRequest timeRange, String flamegraphName) {
        GraphInfo graphInfo = GraphInfo.custom(primaryProfileInfo.id(), eventType, flamegraphName);
        Config config = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryRecording(secondaryRecording)
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withEventType(eventType)
                .withTimeRange(TimeRange.create(timeRange.start(), timeRange.end(), timeRange.absoluteTime()))
                .build();

        generateAndSave(graphInfo, () -> generator.generate(config));
    }

    @Override
    public ArrayNode timeseries(EventType eventType, boolean weightValueMode) {
        Config timeseriesConfig = Config.differentialBuilder()
                .withPrimaryRecording(primaryRecording)
                .withSecondaryRecording(secondaryRecording)
                .withEventType(eventType)
                .withPrimaryStart(primaryProfileInfo.startedAt())
                .withSecondaryStart(secondaryProfileInfo.startedAt())
                .withCollectWeight(weightValueMode)
                .build();

        return timeseriesGenerator.generate(timeseriesConfig);
    }

    @Override
    public ArrayNode timeseries(EventType eventType, String searchPattern, boolean weightValueMode) {
        return null;
    }

    @Override
    public String generateFilename(EventType eventType) {
        return primaryProfileInfo.id() + "-diff-" + eventType.code() + "-" + TimeUtils.currentDateTime();
    }
}
