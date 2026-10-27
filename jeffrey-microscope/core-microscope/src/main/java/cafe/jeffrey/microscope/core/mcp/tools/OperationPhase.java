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

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Which step of its work an operation is on. The state says whether it is running at all; the phase
 * says what it is doing, so a caller can tell a copy from a parse from the rename after it.
 * <p>
 * The workers record the phase as its {@link #code()}, because the operation snapshot they publish is
 * shared with the profile pipeline, which knows nothing of the MCP layer; {@link #ofCode} reads it
 * back where the snapshot is rendered.
 */
public enum OperationPhase {

    /** Waiting for a slot; the work has not started. */
    QUEUED,

    /** Started, and has not said more than that. */
    RUNNING,

    /** Never scheduled: the attempt failed before its work could begin. */
    NOT_STARTED,

    /** Nothing to run: the result was already on this machine, and was recorded as this attempt's. */
    ALREADY_AVAILABLE,

    /** A profile pipeline's run, whose stages are in the details. */
    PIPELINE,

    /** Copying a recording file into the Quick Analysis store. */
    IMPORTING,

    /** Parsing a stored recording into a profile. */
    ANALYZING,

    /** The profile is built; the short work after it, such as applying a requested name, is running. */
    FINALIZING,

    /** Transferring a hub session's recording to this machine. */
    DOWNLOADING,

    /** Transferring one file of a hub session to this machine. */
    FETCHING;

    private static final Map<String, OperationPhase> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OperationPhase::code, Function.identity()));

    /** How a worker records this phase in the snapshot it publishes. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalStateException for a code no phase records, which is a worker naming a phase this
     *                               enum does not know
     */
    public static OperationPhase ofCode(String code) {
        OperationPhase phase = BY_CODE.get(code);
        if (phase == null) {
            throw new IllegalStateException("An operation reported an unknown phase: phase=" + code);
        }
        return phase;
    }
}
