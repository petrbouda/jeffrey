/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.heapdump.model.HeapDumpConfig;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;

/**
 * Heap-dump lifecycle endpoints: existence/status checks, initialization and re-initialization,
 * and configuration. The dump itself arrives as a recording of its own; a profile is never given
 * one afterwards.
 * <p>
 * Object-graph browsing lives in {@link HeapDumpObjectsController}; analysis
 * reports live in {@link HeapDumpAnalysisController}.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap")
public class HeapDumpController {

    private final ProfileManagerResolver resolver;

    private final HeapDumpInitService initService;

    public HeapDumpController(ProfileManagerResolver resolver, HeapDumpInitService initService) {
        this.resolver = resolver;
        this.initService = initService;
    }

    @GetMapping("/exists")
    public boolean exists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).heapDumpExists();
    }

    @GetMapping("/cache-ready")
    public boolean cacheReady(@PathVariable("profileId") String profileId) {
        return mgr(profileId).isCacheReady();
    }

    @GetMapping("/summary")
    public HeapSummary summary(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getSummary();
    }

    @PostMapping("/unload")
    public void unload(@PathVariable("profileId") String profileId) {
        mgr(profileId).unloadHeap();
    }

    /**
     * Starts the full initialization pipeline (index build, dominator tree and
     * every cached analysis) as a background run, compressed oops auto-detected;
     * poll {@code /init-progress} for per-stage statuses. Returns 202 whether a new
     * run was started or one was already in flight. Forcing compressed oops is
     * {@code /reinitialize}.
     */
    @PostMapping("/initialize-all")
    public ResponseEntity<Void> initializeAll(@PathVariable("profileId") String profileId) {
        initService.start(profileId, mgr(profileId), null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Drops the built index and every cached analysis and starts the full pipeline again, optionally
     * forcing compressed oops on or off when auto-detection guessed wrong. Returns 202 whether a new
     * run was started or one was already in flight; the in-flight one is left to finish.
     */
    @PostMapping("/reinitialize")
    public ResponseEntity<Void> reinitialize(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "compressedOops", required = false) Boolean compressedOops) {
        initService.reinitialize(profileId, mgr(profileId), compressedOops);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/init-progress")
    public PipelineProgress initProgress(@PathVariable("profileId") String profileId) {
        return initService.progress(profileId);
    }

    @GetMapping("/config")
    public HeapDumpConfig getConfig(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getHeapDumpConfig().orElse(null);
    }

    /**
     * The last run's stage timings. Written by the pipeline itself now that runs are backend-owned, so
     * there is no companion POST — a snapshot the frontend could supply would be a second, unverified
     * account of work the backend already recorded.
     */
    @GetMapping("/init-result")
    public PipelineRunResult getInitPipelineResult(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getInitPipelineResult().orElse(null);
    }

    private HeapDumpManager mgr(String profileId) {
        return resolver.resolve(profileId).heapDumpManager();
    }
}
