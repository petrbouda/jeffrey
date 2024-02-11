package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.repository.HeatmapInfo;
import pbouda.jeffrey.repository.ProfileInfo;

import java.util.List;
import java.util.function.Function;

public interface HeatmapManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, HeatmapManager> {
    }

    List<HeatmapInfo> all();

    byte[] contentByName(String heatmapName, EventType eventType);

    void delete(String heatmapId);

    void cleanup();

}
