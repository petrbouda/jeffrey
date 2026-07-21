/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.heapdump.model.HeapDumpConfig;
import cafe.jeffrey.profile.heapdump.model.HeapDumpInitProgress;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.InitPipelineResult;
import cafe.jeffrey.profile.heapdump.model.InitializeResult;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;

/**
 * Heap-dump lifecycle endpoints: existence/status checks, upload, initialization,
 * configuration and cache management.
 * <p>
 * Object-graph browsing lives in {@link HeapDumpObjectsController}; analysis
 * reports live in {@link HeapDumpAnalysisController}.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/heap")
public class HeapDumpController {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpController.class);

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

    @PostMapping("/delete-cache")
    public void deleteCache(@PathVariable("profileId") String profileId) {
        mgr(profileId).deleteCache();
    }

    @PostMapping("/delete")
    public void deleteHeapDump(@PathVariable("profileId") String profileId) {
        mgr(profileId).deleteHeapDump();
    }

    @PostMapping("/sanitize")
    public void sanitize(@PathVariable("profileId") String profileId) {
        mgr(profileId).sanitizeHeapDump();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadHeapDump(
            @PathVariable("profileId") String profileId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw Exceptions.invalidRequest("File is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw Exceptions.invalidRequest("Filename is required");
        }
        LOG.debug("Uploading heap dump: filename={}", filename);
        try {
            mgr(profileId).uploadHeapDump(file.getInputStream(), filename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded heap dump", e);
        }
    }

    @PostMapping("/initialize")
    public InitializeResult initialize(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "compressedOops", required = false) Boolean compressedOops) {
        return mgr(profileId).initialize(compressedOops);
    }

    /**
     * Starts the full initialization pipeline (index build, dominator tree and
     * every cached analysis) as a background run; poll {@code /init-progress}
     * for per-stage statuses. Returns 202 whether a new run was started or one
     * was already in flight.
     */
    @PostMapping("/initialize-all")
    public ResponseEntity<Void> initializeAll(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "compressedOops", required = false) Boolean compressedOops) {
        initService.start(profileId, mgr(profileId), compressedOops);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/init-progress")
    public HeapDumpInitProgress initProgress(@PathVariable("profileId") String profileId) {
        return initService.progress(profileId);
    }

    @GetMapping("/config")
    public HeapDumpConfig getConfig(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getHeapDumpConfig().orElse(null);
    }

    @GetMapping("/init-result/exists")
    public boolean initPipelineResultExists(@PathVariable("profileId") String profileId) {
        return mgr(profileId).initPipelineResultExists();
    }

    @GetMapping("/init-result")
    public InitPipelineResult getInitPipelineResult(@PathVariable("profileId") String profileId) {
        return mgr(profileId).getInitPipelineResult().orElse(null);
    }

    @PostMapping("/init-result")
    public void storeInitPipelineResult(
            @PathVariable("profileId") String profileId,
            @RequestBody InitPipelineResult result) {
        mgr(profileId).storeInitPipelineResult(result);
    }

    private HeapDumpManager mgr(String profileId) {
        return resolver.resolve(profileId).heapDumpManager();
    }
}
