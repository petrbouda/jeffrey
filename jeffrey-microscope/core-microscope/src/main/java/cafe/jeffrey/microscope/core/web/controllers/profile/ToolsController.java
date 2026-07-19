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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.ProfileToolsManager.RenamePreviewResult;
import cafe.jeffrey.profile.manager.ProfileToolsManager.RenameRequest;
import cafe.jeffrey.profile.manager.ProfileToolsManager.RenameResult;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapseApplyResult;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapsePreviewResult;
import cafe.jeffrey.profile.tools.collapse.CollapseFramesManager.CollapseRequest;
import cafe.jeffrey.profile.tools.otlp.OtlpExportManager.OtlpExportEventType;
import cafe.jeffrey.profile.tools.pprof.PprofExportManager.PprofExportEventType;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/tools")
public class ToolsController {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsController.class);

    private static final String PPROF_FILE_SUFFIX = ".pb.gz";
    private static final String OTLP_FILE_SUFFIX = ".otlp";
    private static final String EVENT_TYPE_NAMESPACE_SEPARATOR = ".";
    private static final String FILENAME_FALLBACK = "profile";
    private static final String FILENAME_SANITIZE_PATTERN = "[^a-z0-9_-]";
    private static final String FILENAME_SANITIZE_REPLACEMENT = "_";

    private final ProfileManagerResolver resolver;
    private final RecordingsCoreManager recordingsManager;

    public ToolsController(ProfileManagerResolver resolver, RecordingsCoreManager recordingsManager) {
        this.resolver = resolver;
        this.recordingsManager = recordingsManager;
    }

    public record PprofExportRequest(String eventType, boolean includeWeight) {
    }

    public record OtlpExportRequest(String eventType, boolean includeWeight) {
    }

    public record AddToRecordingsResponse(String recordingId) {
    }

    @GetMapping("/pprof/event-types")
    public List<PprofExportEventType> pprofEventTypes(@PathVariable("profileId") String profileId) {
        LOG.debug("Listing stack-based event types for pprof export: profileId={}", profileId);
        return resolver.resolve(profileId).pprofExportManager().stackBasedEventTypes();
    }

    @PostMapping(value = "/pprof/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadPprof(
            @PathVariable("profileId") String profileId,
            @RequestBody PprofExportRequest request) {
        LOG.info("Exporting pprof for download: profileId={} eventType={} includeWeight={}",
                profileId, request.eventType(), request.includeWeight());
        ProfileManager pm = resolver.resolve(profileId);
        byte[] pprof = pm.pprofExportManager().export(request.eventType(), request.includeWeight());
        String filename = pprofFilename(pm.info().name(), request.eventType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(pprof);
    }

    @PostMapping(value = "/pprof/add-to-recordings", produces = MediaType.APPLICATION_JSON_VALUE)
    public AddToRecordingsResponse addPprofToRecordings(
            @PathVariable("profileId") String profileId,
            @RequestBody PprofExportRequest request) {
        LOG.info("Exporting pprof into recordings: profileId={} eventType={} includeWeight={}",
                profileId, request.eventType(), request.includeWeight());
        ProfileManager pm = resolver.resolve(profileId);
        byte[] pprof = pm.pprofExportManager().export(request.eventType(), request.includeWeight());
        String filename = pprofFilename(pm.info().name(), request.eventType());
        String recordingId = recordingsManager.uploadRecording(filename, new ByteArrayInputStream(pprof), null);
        LOG.info("Added exported pprof to recordings: profileId={} recordingId={}", profileId, recordingId);
        return new AddToRecordingsResponse(recordingId);
    }

    @GetMapping("/otlp/event-types")
    public List<OtlpExportEventType> otlpEventTypes(@PathVariable("profileId") String profileId) {
        LOG.debug("Listing stack-based event types for OTLP export: profileId={}", profileId);
        return resolver.resolve(profileId).otlpExportManager().stackBasedEventTypes();
    }

    @PostMapping(value = "/otlp/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadOtlp(
            @PathVariable("profileId") String profileId,
            @RequestBody OtlpExportRequest request) {
        LOG.info("Exporting OTLP for download: profileId={} eventType={} includeWeight={}",
                profileId, request.eventType(), request.includeWeight());
        ProfileManager pm = resolver.resolve(profileId);
        byte[] otlp = pm.otlpExportManager().export(request.eventType(), request.includeWeight());
        String filename = otlpFilename(pm.info().name(), request.eventType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(otlp);
    }

    @PostMapping(value = "/otlp/add-to-recordings", produces = MediaType.APPLICATION_JSON_VALUE)
    public AddToRecordingsResponse addOtlpToRecordings(
            @PathVariable("profileId") String profileId,
            @RequestBody OtlpExportRequest request) {
        LOG.info("Exporting OTLP into recordings: profileId={} eventType={} includeWeight={}",
                profileId, request.eventType(), request.includeWeight());
        ProfileManager pm = resolver.resolve(profileId);
        byte[] otlp = pm.otlpExportManager().export(request.eventType(), request.includeWeight());
        String filename = otlpFilename(pm.info().name(), request.eventType());
        String recordingId = recordingsManager.uploadRecording(filename, new ByteArrayInputStream(otlp), null);
        LOG.info("Added exported OTLP to recordings: profileId={} recordingId={}", profileId, recordingId);
        return new AddToRecordingsResponse(recordingId);
    }

    private static String pprofFilename(String profileName, String eventType) {
        return sanitize(profileName) + "-" + sanitize(eventTypeShort(eventType)) + PPROF_FILE_SUFFIX;
    }

    private static String otlpFilename(String profileName, String eventType) {
        return sanitize(profileName) + "-" + sanitize(eventTypeShort(eventType)) + OTLP_FILE_SUFFIX;
    }

    private static String eventTypeShort(String eventType) {
        int lastDot = eventType.lastIndexOf(EVENT_TYPE_NAMESPACE_SEPARATOR);
        return lastDot >= 0 ? eventType.substring(lastDot + 1) : eventType;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return FILENAME_FALLBACK;
        }
        return value.toLowerCase(Locale.ROOT).replaceAll(FILENAME_SANITIZE_PATTERN, FILENAME_SANITIZE_REPLACEMENT);
    }

    @PostMapping("/rename-frames/preview")
    public RenamePreviewResult previewRename(
            @PathVariable("profileId") String profileId,
            @RequestBody RenameRequest request) {
        LOG.debug("Previewing frame rename: search={} replacement={}", request.search(), request.replacement());
        return resolver.resolve(profileId).toolsManager().previewRename(request);
    }

    @PostMapping("/rename-frames")
    public RenameResult executeRename(
            @PathVariable("profileId") String profileId,
            @RequestBody RenameRequest request) {
        LOG.info("Executing frame rename: search={} replacement={}", request.search(), request.replacement());
        ProfileManager pm = resolver.resolve(profileId);
        RenameResult result = pm.toolsManager().executeRename(request);
        pm.markModified();
        return result;
    }

    @PostMapping("/collapse-frames/preview")
    public CollapsePreviewResult previewCollapse(
            @PathVariable("profileId") String profileId,
            @RequestBody CollapseRequest request) {
        LOG.debug("Previewing frame collapse: patterns={} label={}", request.patterns(), request.label());
        return resolver.resolve(profileId).collapseFramesManager().preview(request);
    }

    @PostMapping("/collapse-frames")
    public CollapseApplyResult executeCollapse(
            @PathVariable("profileId") String profileId,
            @RequestBody CollapseRequest request) {
        LOG.info("Executing frame collapse: patterns={} label={}", request.patterns(), request.label());
        ProfileManager pm = resolver.resolve(profileId);
        CollapseApplyResult result = pm.collapseFramesManager().execute(request);
        pm.markModified();
        return result;
    }
}
