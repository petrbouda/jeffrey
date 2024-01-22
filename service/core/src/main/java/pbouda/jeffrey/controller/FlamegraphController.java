package pbouda.jeffrey.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.controller.model.*;
import pbouda.jeffrey.flamegraph.FlamegraphGenerator;
import pbouda.jeffrey.manager.EventType;
import pbouda.jeffrey.manager.FlamegraphsManager;
import pbouda.jeffrey.manager.ProfileManager;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.repository.FlamegraphInfo;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/flamegraph")
public class FlamegraphController {

    private static final Logger LOG = LoggerFactory.getLogger(FlamegraphController.class);

    private final FlamegraphGenerator generator;
    private final ProfilesManager profilesManager;

    @Autowired
    public FlamegraphController(FlamegraphGenerator generator, ProfilesManager profilesManager) {
        this.generator = generator;
        this.profilesManager = profilesManager;
    }

    @PostMapping
    public ResponseEntity<List<FlamegraphInfo>> list(@RequestBody FlamegraphListRequest request) {
        return profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .map(manager -> ResponseEntity.ok(manager.all()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/single")
    public ResponseEntity<String> getContent(@RequestBody GetFlamegraphRequest request) {
        return profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .flatMap(manager -> manager.content(request.flamegraphId()))
                .map(bytes -> ResponseEntity.ok(new String(bytes)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/generate/predefined")
    public ResponseEntity<byte[]> getPredefined(@RequestBody GeneratePredefinedRequest request) {
        EventType eventType = new EventType(request.eventType());

        Optional<ProfileManager> managerProfileOpt = profilesManager.getProfile(request.profileId());
        if (managerProfileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProfileManager profileManager = managerProfileOpt.get();
        FlamegraphsManager flamegraphsManager = profileManager.flamegraphManager();
        Optional<byte[]> flamegraphOpt = flamegraphsManager.content(eventType);
        if (flamegraphOpt.isPresent()) {
            return ResponseEntity.ok(flamegraphOpt.get());
        } else {
            byte[] content = generator.generate(profileManager.info().profilePath(), eventType);
            flamegraphsManager.upload(eventType, content);
            return ResponseEntity.ok(content);
        }
    }

    @PostMapping("/delete")
    @SuppressWarnings("rawtypes")
    public ResponseEntity delete(@RequestBody DeleteFlamegraphRequest request) throws IOException {
        return profilesManager.getProfile(request.profileId())
                .map(ProfileManager::flamegraphManager)
                .map(manager -> {
                    manager.delete(request.flamegraphId());
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/generateRange")
    public ResponseEntity<List<FlamegraphInfo>> generateRange(@RequestBody GenerateWithRangeRequest request) {
        EventType eventType = new EventType(request.eventType());

        Optional<ProfileManager> managerProfileOpt = profilesManager.getProfile(request.profileId());
        if (managerProfileOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ProfileManager profileManager = managerProfileOpt.get();
        FlamegraphsManager flamegraphsManager = profileManager.flamegraphManager();
        var flamegraphInfo = new FlamegraphInfo(request.profileId(), request.flamegraphName());

        TimeRange timeRange = request.timeRange();
        byte[] content = generator.generate(profileManager.info().profilePath(), eventType, millis(timeRange.start()), millis(timeRange.end()));
        flamegraphsManager.upload(flamegraphInfo, content);
        LOG.info("Flamegraph generated: {}", flamegraphInfo);

        return ResponseEntity.ok(flamegraphsManager.all());
    }

    private static long millis(int[] time) {
        return millis(time[0], time[1]);
    }

    private static long millis(int seconds, int millis) {
        return seconds * 1000L + millis;
    }
}
