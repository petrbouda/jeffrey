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

package cafe.jeffrey.microscope.core.mcp.tools.connector;

import cafe.jeffrey.microscope.core.mcp.tools.CatalogueStatus;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.util.List;

/**
 * What the {@code connector_} tools answer: one record per tool, each carrying the calls to make next and
 * the connector's page for the user. The sessions and files are listed as rows an agent reads;
 * a download or a fetch is reported in one shape whatever state its transfer is in, so a caller that
 * waited and one that polled parse the same record.
 */
public final class ConnectorAnswers {

    private ConnectorAnswers() {
    }

    /** Whether a connector is configured, and whether it answered the probe just now. */
    public enum ConnectorState {
        /** Configuration declares no connector; the reason says how to declare one. */
        NOT_CONFIGURED,
        /** It answered; connector_sessions can list its sessions. */
        REACHABLE,
        /** It did not answer, so connector_sessions can list nothing from it. */
        UNREACHABLE
    }

    /** The one connector this installation's configuration declares, and whether it answers right now. */
    public record ConnectorStatus(
            ConnectorState state,
            @McpNullable
            @McpDescription("How to declare the connector in configuration when none is; null otherwise")
            String reason,
            @McpNullable
            @McpDescription("What the connector is called; null when none is configured")
            String name,
            @McpNullable
            @McpDescription("Which storage the connector reads: AZURE_FILES (an Azure file share) or LOCAL (a "
                    + "directory on this machine); null when none is configured")
            ConnectorKind kind,
            @McpNullable
            @McpDescription("Where the recording volume lies, prefixed by the connector kind: an Azure file share as "
                    + "azure-files:<endpoint>/<share>[/<root>], a directory on this machine as local:<absolute path>; "
                    + "null when none is configured")
            String address,
            @McpNullable
            @McpDescription("True for a connector declared read-only: its sessions list and download as usual, but "
                    + "nothing on it can be deleted; null when none is configured")
            Boolean readOnly,
            @McpNullable
            @McpDescription("The version the connector reported; null when it did not answer or none is configured")
            String version,
            McpFollowUp followUp,
            @McpDescription("The connector's page in the Microscope UI, for the user")
            String uiLink) {
    }

    /** One recording session of one project on the connector. */
    public record SessionRow(
            String workspace,
            String project,
            @McpNullable
            @McpDescription("When the session started recording, as UTC epoch milliseconds")
            Long startedAtEpochMs,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("Span in milliseconds from its start to the end of its recorded data; null while it "
                    + "has no recording")
            Long durationMs,
            @McpDescription("Whether it is its instance's newest session: no newer one has started, so it may "
                    + "still grow")
            boolean newest,
            @McpMinimum(0)
            int files,
            @McpMinimum(0)
            long sizeBytes,
            @McpNullable
            @McpDescription("The local recording downloaded from this session; null when none")
            String recordingId,
            @McpNullable
            @McpDescription("The profile built from this session's download; null when it is not analysed here")
            String profileId,
            @McpDescription("Pass to connector_download and connector_files")
            String sessionRef) {
    }

    /** A page of the live session catalogue across the connector's projects. */
    public record Sessions(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing is listed; null when status is OK")
            String reason,
            List<SessionRow> sessions,
            @McpMinimum(0)
            int returned,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("Sessions matching the filters; null when a connector scope did not answer")
            Integer total,
            @McpMinimum(0)
            int observedTotal,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass unchanged with the same filters; null at the end")
            String nextCursor,
            @McpDescription("Whether every connector scope answered, independently of hasMore")
            boolean complete,
            List<ConnectorSessionScan.Failure> failures,
            McpFollowUp followUp,
            @McpDescription("The connector's page in the Microscope UI, for the user")
            String uiLink) {
    }

    /** How a file of a session is reached. */
    public enum Fetchability {
        /** An artifact: its fileId goes to connector_fetchFile. */
        FETCH,
        /** A recording chunk, taken with the rest of the session by connector_download. */
        DOWNLOAD,
        /** Never fetched one at a time; connector_download brings it with the session. */
        NEVER
    }

    public record FileRow(
            @McpDescription("Pass to connector_fetchFile, or to connector_download as one of fileIds")
            String fileId,
            String name,
            ManagedFile type,
            FileCategory category,
            @McpDescription("Whether it is the newest recording of its instance's newest session, which the connector "
                    + "keeps uncompressed and may still be writing into place")
            boolean newestRecording,
            @McpMinimum(0)
            long sizeBytes,
            @McpNullable
            @McpDescription("When the connector created the file, as UTC epoch milliseconds")
            Long createdAtEpochMs,
            @McpNullable
            @McpDescription("The absolute path of this file on the machine Jeffrey runs on, when it is already "
                    + "here; null otherwise. A chunk that came with a download is named by the session's "
                    + "recordingId or profileId instead")
            String localPath,
            Fetchability fetch) {
    }

    /** A page of the files one session on the connector holds. */
    public record SessionFiles(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing is listed; null when status is OK")
            String reason,
            String sessionRef,
            String sessionName,
            String project,
            @McpNullable
            @McpDescription("The local recording downloaded from this session; null when none")
            String recordingId,
            @McpNullable
            @McpDescription("The profile built from it; null when it is not analysed")
            String profileId,
            @McpNullable
            @McpDescription("That profile's recording start, as UTC epoch milliseconds: an uptime in a GC log "
                    + "of this session is this instant plus the uptime. Null without a profile or a start")
            Long profilingStartedAtEpochMs,
            List<FileRow> files,
            @McpMinimum(0)
            int returned,
            @McpMinimum(0)
            int total,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass unchanged as cursor with the same sessionRef; null at the end")
            String nextCursor,
            McpFollowUp followUp,
            @McpDescription("The connector's page in the Microscope UI, for the user")
            String uiLink) {
    }

    /** Where a download stands. */
    public enum DownloadStatus {
        /** The recording is here: recordingId names it, and profileId too once it is analysed. */
        DOWNLOADED,
        /** The transfer outlasted the call; operationId follows it. */
        RUNNING,
        /** The user chose not to bring any part of the session; nothing was transferred. */
        NOT_DOWNLOADED,
        /** The transfer failed; the same call with retry=true starts a new one. */
        FAILED,
        /** The transfer was cancelled; the same call with retry=true starts a new one. */
        CANCELLED,
        /**
         * STARTUP was asked for and the connector no longer holds the session's first chunk; nothing was
         * transferred, and chosen names the oldest chunk still kept.
         */
        STARTUP_NOT_RETAINED
    }

    /**
     * What a predefined window picked, and why: the window, the evidence for the choice, and for a
     * window that picks one chunk — STARTUP, LATEST, PEAK — that chunk with the figures behind it.
     */
    public record ChosenWindow(
            DownloadWindow window,
            @McpDescription("Why this part, in words: the chunk's place and, for PEAK, its size against the rest")
            String evidence,
            @McpNullable
            @McpDescription("The chunk picked, as a connector_files fileId; for STARTUP_NOT_RETAINED the oldest one kept; "
                    + "null for a window that is not one chunk")
            String chunkFileId,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("That chunk's size on the connector; null for a window that is not one chunk")
            Long chunkSizeBytes,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("For PEAK, the median size of the compressed chunks it was compared with; null otherwise")
            Long medianChunkSizeBytes,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("The session's chunk length, measured from its chunk starts; null when not measured")
            Long chunkLengthMs) {

        public ChosenWindow {
            if (window == null || evidence == null || evidence.isBlank()) {
                throw new IllegalArgumentException("a chosen window needs its window and evidence: window=" + window);
            }
        }

        /** A window that is a span or the whole session, not one chunk. */
        static ChosenWindow span(DownloadWindow window, String evidence) {
            return new ChosenWindow(window, evidence, null, null, null, null);
        }
    }

    /**
     * One download of a session on the connector, whatever state it is in. The part asked for is echoed as the
     * arguments that name the same download again, so a later call joins this transfer or returns the
     * recording it made.
     */
    public record Download(
            DownloadStatus status,
            @McpNullable
            @McpDescription("What the status means when it is not obvious from the status alone")
            String reason,
            String sessionRef,
            String sessionId,
            @McpNullable
            String sessionName,
            @McpNullable
            String project,
            @McpNullable
            @McpDescription("The window asked for, start, as UTC epoch milliseconds; null for no lower bound")
            Long startEpochMs,
            @McpNullable
            @McpDescription("The window asked for, end, as UTC epoch milliseconds; null for no upper bound")
            Long endEpochMs,
            @McpDescription("The files asked for by id; empty for a window or the whole session")
            List<String> fileIds,
            @McpNullable
            @McpDescription("The local recording; null until the transfer lands")
            String recordingId,
            @McpNullable
            @McpDescription("The profile already built from it; null when it is not analysed")
            String profileId,
            @McpNullable
            @McpDescription("The bundle the download landed in, holding every item it brought; null until "
                    + "the transfer lands, and for a copy made before downloads were bundled")
            String bundleId,
            @McpDescription("Every item of the bundle that is analysed on its own - the recording first, then "
                    + "the heap dumps and the perf counters by name - with its recordingId for "
                    + "recordings_analyzeRecording; empty until the transfer lands")
            List<BundleItem> items,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("JFR chunks the recording holds; null when this answer did not count them")
            Integer recordingFiles,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("Artifacts brought beside the chunks; null when this answer did not count them")
            Integer artifactFiles,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("Bytes transferred, or to transfer while RUNNING; null when not counted")
            Long sizeBytes,
            @McpNullable
            @McpDescription("Where the downloaded recording's data starts, as UTC epoch milliseconds: read from "
                    + "the recording itself once it is here, else where a part's first chunk starts; null when "
                    + "neither is known")
            Long coveredStartEpochMs,
            @McpNullable
            @McpDescription("Where the downloaded recording's data ends, as UTC epoch milliseconds: read from the "
                    + "recording itself once it is here. Before that, a part's estimate - its last chunk's start "
                    + "plus one measured chunk length, more than a last chunk cut short holds - and null for the "
                    + "whole session and for a last chunk nothing bounds")
            Long coveredEndEpochMs,
            @McpNullable
            @McpDescription("What a predefined window picked and why; null when the call named the part itself or "
                    + "took the whole session without a window")
            ChosenWindow chosen,
            @McpNullable
            @McpDescription("The transfer behind this answer, for operations_status and operations_cancel")
            String operationId,
            @McpNullable
            @McpDescription("That transfer as operations_status reports it")
            McpOperationRegistry.Snapshot operation,
            McpFollowUp followUp,
            @McpDescription("The profile once analysed, else the recordings page once downloaded, else the connector "
                    + "browser, in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * One item of a downloaded bundle: a recording of its own, analysed into a profile of its own.
     */
    public record BundleItem(
            String recordingId,
            @McpDescription("What the item is: JDK or ASYNC_PROFILER for the session's recording, HEAP_DUMP for "
                    + "a heap dump, PERF_COUNTERS for the perf counters")
            RecordingEventSource eventSource,
            String name,
            @McpNullable
            @McpDescription("The profile built from the item; null until it is analysed")
            String profileId) {
    }

    /** Where a fetch stands. */
    public enum FetchStatus {
        /** The file is at path. */
        FETCHED,
        /** The transfer outlasted the call; the file will land at path, and operationId follows it. */
        RUNNING
    }

    /** One artifact of a session on the connector, fetched onto this machine or on its way. */
    public record Fetch(
            FetchStatus status,
            String sessionRef,
            String fileId,
            String filename,
            ManagedFile type,
            @McpNullable
            @McpMinimum(0)
            Long sizeBytes,
            @McpDescription("The absolute path of the file on the machine Jeffrey runs on: where it is, or where "
                    + "it lands while RUNNING. Read it with your own tools")
            String path,
            @McpDescription("True when nothing was transferred because the file was already at path")
            boolean alreadyHere,
            @McpNullable
            @McpDescription("The local recording downloaded from this session; null when none")
            String recordingId,
            @McpNullable
            @McpDescription("The profile built from it; null when it is not analysed")
            String profileId,
            @McpNullable
            @McpDescription("That profile's recording start, as UTC epoch milliseconds: an uptime in a GC log "
                    + "is this instant plus the uptime. Null without a profile or a start")
            Long profilingStartedAtEpochMs,
            @McpNullable
            @McpDescription("The transfer behind this answer, for operations_status and operations_cancel")
            String operationId,
            @McpNullable
            @McpDescription("That transfer as operations_status reports it")
            McpOperationRegistry.Snapshot operation,
            McpFollowUp followUp,
            @McpDescription("The connector's page in the Microscope UI, for the user")
            String uiLink) {
    }
}
