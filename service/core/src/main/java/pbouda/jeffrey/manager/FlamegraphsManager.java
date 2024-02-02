package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.repository.FlamegraphInfo;

import java.util.List;
import java.util.Optional;

public interface FlamegraphsManager {

    List<FlamegraphInfo> all();

    Optional<ObjectNode> content(String flamegraphId);

    Optional<ObjectNode> content(EventType eventType);

    void upload(FlamegraphInfo flamegraphInfo, ObjectNode content);

    void upload(EventType eventType, ObjectNode content);

    void export(String flamegraphId);

    void export(EventType eventType);

    void delete(String flamegraphId);
}
