package pbouda.jeffrey.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.common.EventType;
import pbouda.jeffrey.controller.model.GetHeatmapRequest;
import pbouda.jeffrey.generator.heatmap.HeatmapConfig;
import pbouda.jeffrey.generator.heatmap.api.HeatmapGenerator;
import pbouda.jeffrey.manager.HeatmapManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.HeatmapInfo;
import pbouda.jeffrey.repository.ProfileInfo;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/heatmap")
public class HeatmapController {

    private final HeatmapGenerator heatmapGenerator;
    private final ProfilesManager profilesManager;

    @Autowired
    public HeatmapController(HeatmapGenerator heatmapGenerator, ProfilesManager profilesManager) {
        this.heatmapGenerator = heatmapGenerator;
        this.profilesManager = profilesManager;
    }

    @PostMapping("/startup")
    public ResponseEntity<String> startup(@RequestBody GetHeatmapRequest request) {
        EventType eventType = new EventType(request.eventType());

        Optional<ProfileManager> profileManagerOpt = profilesManager.getProfile(request.profileId());
        if (profileManagerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProfileManager profileManager = profileManagerOpt.get();
        HeatmapManager heatmapManager = profileManagerOpt.get().heatmapManager();
        Optional<byte[]> content = heatmapManager.contentByName(request.heatmapName());

        byte[] result;
        if (content.isPresent()) {
            result = content.get();
        } else {
            ProfileInfo profileInfo = profileManager.info();

            HeatmapConfig heatmapConfig = HeatmapConfig.builder()
                    .withRecording(profileInfo.recordingPath())
                    .withEventType(eventType)
                    .withProfilingStart(profileInfo.startedAt())
                    .withHeatmapStart(Duration.ZERO)
                    .withDuration(Duration.ofMinutes(5))
                    .build();

            result = heatmapGenerator.generate(heatmapConfig);
            heatmapManager.upload(new HeatmapInfo(profileInfo.id(), request.heatmapName()), result);
        }

        return ResponseEntity.ok(new String(result));
    }
}
