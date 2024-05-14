package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.repository.model.HeatmapInfo;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

public interface HeatmapManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, HeatmapManager> {
    }

    List<HeatmapInfo> all();

    byte[] contentByName(String heatmapName, Type eventType);

    void delete(String heatmapId);

    void cleanup();

}
