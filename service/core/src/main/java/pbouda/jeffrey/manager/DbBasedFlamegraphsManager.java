package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.exception.NotFoundException;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.repository.FlamegraphInfo;
import pbouda.jeffrey.repository.FlamegraphRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class DbBasedFlamegraphsManager implements FlamegraphsManager {

    private static final Logger LOG = LoggerFactory.getLogger(DbBasedFlamegraphsManager.class);

    private final ProfileInfo profileInfo;
    private final WorkingDirs workingDirs;
    private final FlamegraphRepository flamegraphRepository;
    private final FlamegraphGenerator flamegraphGenerator;

    public DbBasedFlamegraphsManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            FlamegraphRepository flamegraphRepository,
            FlamegraphGenerator flamegraphGenerator) {
        this.profileInfo = profileInfo;
        this.workingDirs = workingDirs;
        this.flamegraphRepository = flamegraphRepository;
        this.flamegraphGenerator = flamegraphGenerator;
    }

    @Override
    public List<FlamegraphInfo> all() {
        return flamegraphRepository.all(profileInfo.id());
    }

    @Override
    public Optional<ObjectNode> content(String flamegraphId) {
        return flamegraphRepository.content(profileInfo.id(), flamegraphId);
    }

    @Override
    public Optional<ObjectNode> content(EventType eventType) {
        return flamegraphRepository.content(profileInfo.id(), eventType);
    }

    @Override
    public void upload(FlamegraphInfo flamegraphInfo, ObjectNode content) {
        flamegraphRepository.insert(flamegraphInfo, content);
    }

    @Override
    public void upload(EventType eventType, ObjectNode content) {
        flamegraphRepository.insert(profileInfo.id(), eventType, content);
    }

    @Override
    public void export(String flamegraphId) {
        Optional<ObjectNode> content = content(flamegraphId);
        FlamegraphInfo info = flamegraphRepository.info(profileInfo.id(), flamegraphId);
        _export(content, info.name());
    }

    @Override
    public void export(EventType eventType) {
        Optional<ObjectNode> content = content(eventType);
        String eventName = eventType.code().replace(".", "_");
        _export(content, profileInfo.name() + "-" + eventName);
    }

    private void _export(Optional<ObjectNode> content, String flamegraphName) {
        if (content.isEmpty()) {
            throw new NotFoundException("Flamegraph cannot be found to export it: flamegraph=" + flamegraphName);
        }

        Path target = workingDirs.exportsDir().resolve(flamegraphName + ".html");
        flamegraphGenerator.export(target, content.get());
    }

    @Override
    public void delete(String flamegraphId) {
        flamegraphRepository.delete(profileInfo.id(), flamegraphId);
    }
}
