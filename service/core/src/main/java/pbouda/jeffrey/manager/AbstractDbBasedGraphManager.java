package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final GraphRepository repository;
    private final GraphExporter graphExporter;

    public AbstractDbBasedGraphManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            GraphRepository repository,
            GraphExporter graphExporter) {

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

        _export(content);
    }

    @Override
    public void export(EventType eventType) {
        GraphContent content = repository.content(profileInfo.id(), eventType)
                .orElseThrow(() -> new NotFoundException(profileInfo.id(), eventType));

        _export(content);
    }

    private void _export(GraphContent content) {
        Path target = workingDirs.exportsDir().resolve(content.name() + ".html");
        graphExporter.export(target, content);
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
            return Optional.of(new GraphContent(graphInfo.id(), graphInfo.name(), GraphType.FLAMEGRAPH, generated));
        }
    }
}
