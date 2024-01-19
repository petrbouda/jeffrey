package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.HeatmapInfo;

import java.util.List;
import java.util.Optional;

public interface HeatmapManager {

    List<HeatmapInfo> all();

    Optional<byte[]> content(String heatmapId);

    Optional<byte[]> contentByName(String heatmapName);

    void upload(HeatmapInfo heatmapInfo, byte[] content);

    void delete(String heatmapId);

}
