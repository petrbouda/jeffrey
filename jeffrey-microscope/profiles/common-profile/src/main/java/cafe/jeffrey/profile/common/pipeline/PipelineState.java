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

package cafe.jeffrey.profile.common.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a whole pipeline run has got to. {@link #IDLE} never belongs to a run — it is what the progress
 * endpoint answers for a key that has never run, so the UI can tell "never started" apart from
 * "finished", which a missing snapshot alone cannot express.
 */
public enum PipelineState {

    IDLE("idle"),
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String code;

    PipelineState(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static PipelineState fromCode(String code) {
        for (PipelineState state : values()) {
            if (state.code.equals(code)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown pipeline state: " + code);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
