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
import cafe.jeffrey.profile.common.pipeline.StageProgress;
import cafe.jeffrey.profile.common.pipeline.StageStatus;

import java.util.List;

/**
 * One stage of the pipeline that builds a profile or prepares a heap dump, as an answer reports it.
 *
 * @param id         the stage's name, as the pipeline defines it
 * @param status     where the stage has got to
 * @param durationMs how long the stage took; null while it has not finished
 */
public record PipelineStage(
        String id,
        Status status,
        @McpNullable
        @McpMinimum(0)
        @McpDescription("How long the stage took, in milliseconds; null while it has not finished")
        Long durationMs) {

    /**
     * The stage's state on the wire. The pipeline's own {@link StageStatus} writes lowercase codes the
     * web UI compares against, so the MCP layer keeps a constant-named twin of it.
     */
    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        /** Ran to a decision that there was nothing to do: neither a pass nor a failure. */
        SKIPPED
    }

    public static List<PipelineStage> of(List<StageProgress> stages) {
        return stages.stream()
                .map(stage -> new PipelineStage(stage.id(), status(stage.status()), stage.durationMs()))
                .toList();
    }

    private static Status status(StageStatus status) {
        return switch (status) {
            case PENDING -> Status.PENDING;
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case COMPLETED -> Status.COMPLETED;
            case FAILED -> Status.FAILED;
            case SKIPPED -> Status.SKIPPED;
        };
    }
}
