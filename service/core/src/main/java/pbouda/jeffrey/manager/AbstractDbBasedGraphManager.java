package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRange;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.exception.NotFoundException;
import pbouda.jeffrey.graph.GraphExporter;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.repository.GraphRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractDbBasedGraphManager implements GraphManager {

    private final GraphType graphType;
    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final GraphRepository repository;
    private final GraphExporter graphExporter;

    public AbstractDbBasedGraphManager(
            GraphType graphType,
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            GraphExporter graphExporter) {

        this.graphType = graphType;
        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.repository = repository;
        this.graphExporter = graphExporter;
    }

    @Override
    public List<GraphInfo> allCustom() {
        return repository.allCustom(profileInfo.id());
    }

    @Override
    public void export(String flamegraphId) {
        GraphContent content = repository.content(profileInfo.id(), flamegraphId)
                .orElseThrow(() -> new NotFoundException(profileInfo.id(), flamegraphId));

        _export(content.content(), Path.of(content.name() + ".html"));
    }

    protected void _export(JsonNode jsonObject, Path filename) {
        Path target = workingDirs.exportsDir().resolve(filename);
        graphExporter.export(target, jsonObject);
    }

    @Override
    public void export(EventType eventType) {
        ObjectNode content = generate(eventType);
        _export(content, Path.of(generateFilename(eventType) + ".html"));
    }

    @Override
    public void export(EventType eventType, TimeRange timeRange) {
        ObjectNode content = generate(eventType, timeRange);
        _export(content, Path.of(generateFilename(eventType) + ".html"));
    }

    @Override
    public Optional<GraphContent> get(String flamegraphId) {
        return repository.content(profileInfo.id(), flamegraphId);
    }

    @Override
    public void delete(String flamegraphId) {
        repository.delete(profileInfo.id(), flamegraphId);
    }

    @Override
    public void cleanup() {
        repository.deleteByProfileId(profileInfo.id());
    }

    protected Optional<GraphContent> generate(boolean checkExists, GraphInfo graphInfo, Supplier<ObjectNode> generator) {
        Optional<GraphContent> content = repository.content(profileInfo.id(), graphInfo.eventType());
        if (checkExists && content.isPresent()) {
            return content;
        } else {
            ObjectNode generated = generator.get();
            repository.insert(graphInfo, generated);
            return Optional.of(new GraphContent(graphInfo.id(), graphInfo.name(), graphType, generated));
        }
    }

    protected void generateAndSave(GraphInfo graphInfo, Supplier<ObjectNode> generator) {
        ObjectNode generated = generator.get();
        repository.insert(graphInfo, generated);
    }
}
