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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.core.mcp.tools.CatalogueStatus;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.util.List;

/**
 * What the {@code hubs_} tools answer: one record per tool, each carrying the calls to make next and
 * the hub browser page for the user. The hubs, sessions and files are listed as rows an agent reads;
 * a download or a fetch is reported in one shape whatever state its transfer is in, so a caller that
 * waited and one that polled parse the same record.
 */
public final class HubAnswers {

    private HubAnswers() {
    }

    /** Whether a hub answered the version probe just now. */
    public enum HubStatus {
        /** It answered; hubs_sessions can list its sessions. */
        REACHABLE,
        /** It did not answer, so hubs_sessions can list nothing from it. */
        UNREACHABLE
    }

    public record HubRow(
            String name,
            String hubId,
            @McpNullable
            @McpDescription("host:port the hub is reached at; null when none is recorded")
            String address,
            @McpNullable
            @McpDescription("CONFIG for a hub declared in this installation's configuration, which the UI cannot "
                    + "remove; USER for one added by hand")
            HubSource source,
            HubStatus status,
            @McpNullable
            @McpDescription("The version the hub reported; null when it did not answer")
            String hubVersion) {
    }

    /** A page of the connected hubs. */
    public record Hubs(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing is listed; null when status is OK")
            String reason,
            List<HubRow> hubs,
            @McpMinimum(0)
            int returned,
            @McpMinimum(0)
            int total,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass unchanged as cursor; null at the end")
            String nextCursor,
            McpFollowUp followUp,
            @McpDescription("The hub browser in the Microscope UI, for the user")
            String uiLink) {
    }

    /** One recording session of one hub project. */
    public record SessionRow(
            String hub,
            String workspace,
            String project,
            @McpNullable
            @McpDescription("When the session started recording, as UTC epoch milliseconds")
            Long startedAtEpochMs,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("Span in milliseconds; null while the session is still recording")
            Long durationMs,
            @McpNullable
            RecordingStatus status,
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
            @McpDescription("Pass to hubs_download and hubs_files")
            String sessionRef) {
    }

    /** A page of the live session catalogue across every connected hub. */
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
            @McpDescription("Sessions matching the filters; null when a remote scope did not answer")
            Integer total,
            @McpMinimum(0)
            int observedTotal,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass unchanged with the same filters; null at the end")
            String nextCursor,
            @McpDescription("Whether every remote scope answered, independently of hasMore")
            boolean complete,
            List<HubSessionScan.Failure> failures,
            McpFollowUp followUp,
            @McpDescription("The hub browser in the Microscope UI, for the user")
            String uiLink) {
    }

    /** How a file of a session is reached. */
    public enum Fetchability {
        /** An artifact: its fileId goes to hubs_fetchFile. */
        FETCH,
        /** A recording chunk, taken with the rest of the session by hubs_download. */
        DOWNLOAD,
        /** Never fetched one at a time; hubs_download brings it with the session. */
        NEVER
    }

    public record FileRow(
            @McpDescription("Pass to hubs_fetchFile, or to hubs_download as one of fileIds")
            String fileId,
            String name,
            ManagedFile type,
            FileCategory category,
            @McpDescription("ACTIVE for the chunk the session is still writing, FINISHED otherwise")
            RecordingStatus status,
            @McpMinimum(0)
            long sizeBytes,
            @McpNullable
            @McpDescription("When the hub created the file, as UTC epoch milliseconds")
            Long createdAtEpochMs,
            @McpNullable
            @McpDescription("The absolute path of this file on the machine Jeffrey runs on, when it is already "
                    + "here; null otherwise. A chunk that came with a download is named by the session's "
                    + "recordingId or profileId instead")
            String localPath,
            Fetchability fetch) {
    }

    /** A page of the files one hub session holds. */
    public record SessionFiles(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing is listed; null when status is OK")
            String reason,
            String sessionRef,
            String sessionName,
            String hub,
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
            @McpDescription("The hub browser in the Microscope UI, for the user")
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
        CANCELLED
    }

    /**
     * One download of a hub session, whatever state it is in. The part asked for is echoed as the
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
            String hub,
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
            @McpDescription("Where the first downloaded chunk of a part starts, as UTC epoch milliseconds; null "
                    + "for the whole session")
            Long coveredStartEpochMs,
            @McpNullable
            @McpDescription("Where the last downloaded chunk of a part ends, as UTC epoch milliseconds; null for "
                    + "the whole session and for a last chunk nothing bounds")
            Long coveredEndEpochMs,
            @McpNullable
            @McpDescription("The transfer behind this answer, for operations_status and operations_cancel")
            String operationId,
            @McpNullable
            @McpDescription("That transfer as operations_status reports it")
            McpOperationRegistry.Snapshot operation,
            McpFollowUp followUp,
            @McpDescription("The profile once analysed, else the recordings page once downloaded, else the hub "
                    + "browser, in the Microscope UI, for the user")
            String uiLink) {
    }

    /** Where a fetch stands. */
    public enum FetchStatus {
        /** The file is at path. */
        FETCHED,
        /** The transfer outlasted the call; the file will land at path, and operationId follows it. */
        RUNNING
    }

    /** One artifact of a hub session, fetched onto this machine or on its way. */
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
            @McpDescription("The hub browser in the Microscope UI, for the user")
            String uiLink) {
    }
}
