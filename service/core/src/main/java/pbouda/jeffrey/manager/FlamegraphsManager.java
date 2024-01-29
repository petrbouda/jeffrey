package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.repository.FlamegraphInfo;

import java.util.List;
import java.util.Optional;

public interface FlamegraphsManager {

    List<FlamegraphInfo> all();

    Optional<byte[]> content(String flamegraphId);

    Optional<byte[]> content(EventType eventType);

    void upload(FlamegraphInfo flamegraphInfo, byte[] content);

    void upload(EventType eventType, byte[] content);

    void delete(String flamegraphId);
}
