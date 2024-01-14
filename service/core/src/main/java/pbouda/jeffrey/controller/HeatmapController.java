package pbouda.jeffrey.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.GetHeatmapRequest;
import pbouda.jeffrey.service.HeatmapDataManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/heatmap")
public class HeatmapController {

    private final HeatmapDataManager dataManager;

    @Autowired
    public HeatmapController(HeatmapDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @PostMapping("/single")
    public String single(@RequestBody GetHeatmapRequest request) throws IOException {
        boolean alreadyExists = dataManager.exists(request.profile(), request.eventType());

        Path targetPath;
        if (alreadyExists) {
            targetPath = dataManager.dataFilePath(request.profile(), request.eventType());
        } else {
            targetPath = dataManager.createDataFile(request.profile(), request.eventType());
        }

        return Files.readString(targetPath);
    }
}
