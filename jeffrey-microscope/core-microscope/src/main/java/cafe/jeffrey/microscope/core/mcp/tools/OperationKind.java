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

/**
 * The kinds of background work {@link McpOperationRegistry} catalogues, each owned by the tool
 * family that starts it.
 * <p>
 * The wire name is what a client reads back in {@code operations_status} and what every family tool
 * already emitted before this enum existed, so it is fixed here rather than derived from the constant's
 * name: renaming a constant must not rename a field a client has learned to parse. The family is the
 * prefix of the tools that start the operation, which is what decides whether an installation that
 * withholds a family also withholds the operations it would have started.
 */
public enum OperationKind {

    RECORDING_IMPORT(
            "recording_import",
            OperationKind.FAMILY_RECORDINGS,
            "If progress contains a recordingId, call recordings_analyzeRecording with retry=true; "
                    + "otherwise call recordings_analyzeFile again to start a new import."),

    RECORDING_ANALYSIS(
            "recording_analysis",
            OperationKind.FAMILY_RECORDINGS,
            "Call recordings_analyzeRecording with the same recordingId and retry=true to start a new attempt."),

    HUB_DOWNLOAD(
            "hub_download",
            OperationKind.FAMILY_HUBS,
            "Call hubs_download with the same sessionRef and retry=true to start a new attempt."),

    HUB_FETCH(
            "hub_fetch",
            OperationKind.FAMILY_HUBS,
            "Call hubs_fetchFile with the same sessionRef and fileId to start a new attempt."),

    HEAP_PREPARE(
            "heap_prepare",
            OperationKind.FAMILY_HEAP,
            "Call heap_prepare for the same profile/report with retry=true to start a new attempt.");

    /** The tool-family prefixes that own an operation kind, as {@code ExternalMcpProperties} knows them. */
    public static final String FAMILY_RECORDINGS = "recordings";
    public static final String FAMILY_HUBS = "hubs";
    public static final String FAMILY_HEAP = "heap";

    private final String wireName;
    private final String family;
    private final String retryInstruction;

    OperationKind(String wireName, String family, String retryInstruction) {
        this.wireName = wireName;
        this.family = family;
        this.retryInstruction = retryInstruction;
    }

    /** The name a client reads in an operation snapshot; stable across renames of the constant. */
    public String wireName() {
        return wireName;
    }

    /** The prefix of the tool family that starts operations of this kind. */
    public String family() {
        return family;
    }

    /** What a reader is told to do once an operation of this kind has failed or been cancelled. */
    public String retryInstruction() {
        return retryInstruction;
    }

    /**
     * Whether operations of this kind are tied to one recording in the Quick Analysis store, which is
     * what lets {@code recordings_status} find the latest attempt for a recording id.
     */
    public boolean tracksRecording() {
        return FAMILY_RECORDINGS.equals(family);
    }

    /** Whether operations of this kind run on, or reach, a remote Hub. */
    public boolean reachesHub() {
        return FAMILY_HUBS.equals(family);
    }
}
