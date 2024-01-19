package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.FlamegraphInfo;

import java.util.List;
import java.util.Optional;

public interface FlamegraphsManager {

    List<FlamegraphInfo> all();

    Optional<byte[]> content(String flamegraphId);

    void upload(FlamegraphInfo flamegraphInfo, byte[] content);

    void delete(String flamegraphId);
}
