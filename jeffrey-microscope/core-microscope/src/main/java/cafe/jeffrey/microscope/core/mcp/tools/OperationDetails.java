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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;

import java.util.List;
import java.util.Objects;

/**
 * What an operation has to say about its progress beyond its phase: the recording it stored, the
 * stages of the pipeline it runs, the hub session and file it transfers. One shape for every kind,
 * each field null or empty where the kind has nothing to put in it, so a reader parses one record
 * whatever started the operation.
 *
 * @param recordingId       the recording an import stored or an analysis builds from
 * @param stages            the pipeline's stages, empty when the operation runs none
 * @param sessionRef        the hub session a download or a fetch reads
 * @param sessionId         the hub session's id, as the hub knows it
 * @param fileId            the file a fetch transfers
 * @param fileName          that file's name
 * @param sizeBytes         how many bytes the transfer moves
 * @param path              where on this machine a fetch writes the file
 * @param unavailableReason why the progress could not be read, when it could not
 */
public record OperationDetails(
        @McpNullable
        String recordingId,
        List<PipelineStage> stages,
        @McpNullable
        String sessionRef,
        @McpNullable
        String sessionId,
        @McpNullable
        String fileId,
        @McpNullable
        String fileName,
        @McpNullable
        @McpMinimum(0)
        @McpDescription("How many bytes the transfer moves")
        Long sizeBytes,
        @McpNullable
        @McpDescription("Where on the Microscope machine a fetch writes the file")
        String path,
        @McpNullable
        @McpDescription("Why the progress could not be read; null when it was")
        String unavailableReason) {

    /** An operation that reports nothing beyond its phase. */
    public static final OperationDetails NONE =
            new OperationDetails(null, List.of(), null, null, null, null, null, null, null);

    private static final String UNAVAILABLE = "Progress could not be read";

    public OperationDetails {
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
    }

    /** A recording being imported or analysed, with the stages of the parse once it runs. */
    public static OperationDetails recording(String recordingId, List<PipelineStage> stages) {
        return new OperationDetails(recordingId, stages, null, null, null, null, null, null, null);
    }

    /** A profile pipeline's run, read from the pipeline itself. */
    public static OperationDetails pipeline(PipelineProgress progress) {
        return new OperationDetails(null, PipelineStage.of(progress.stages()), null, null, null, null, null, null, null);
    }

    /** A hub session being downloaded into the Quick Analysis store. */
    public static OperationDetails hubDownload(String sessionRef, String sessionId, long sizeBytes) {
        return new OperationDetails(null, List.of(), sessionRef, sessionId, null, null, sizeBytes, null, null);
    }

    /** One file of a hub session being fetched to this machine. */
    public static OperationDetails hubFetch(
            String sessionRef, String fileId, String fileName, long sizeBytes, String path) {
        return new OperationDetails(null, List.of(), sessionRef, null, fileId, fileName, sizeBytes, path, null);
    }

    /** A worker whose progress could not be read; reporting it must never fail the operation. */
    public static OperationDetails unavailable() {
        return new OperationDetails(null, List.of(), null, null, null, null, null, null, UNAVAILABLE);
    }
}
