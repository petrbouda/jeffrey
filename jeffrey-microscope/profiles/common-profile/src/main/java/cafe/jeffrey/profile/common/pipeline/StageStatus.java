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
 * Where one stage of a pipeline run has got to.
 *
 * <p>The wire form is the lowercase {@link #code()} rather than the enum name, because these values
 * were already on the wire before this type existed and the frontend compares against them. Keeping
 * the codes stable is what makes extracting this abstraction a refactor rather than a breaking change.</p>
 */
public enum StageStatus {

    PENDING("pending"),

    IN_PROGRESS("in_progress"),

    COMPLETED("completed"),

    FAILED("failed"),

    /** Ran to a decision that there was nothing to do — distinct from passing, and from failing. */
    SKIPPED("skipped");

    private final String code;

    StageStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static StageStatus fromCode(String code) {
        for (StageStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown stage status: " + code);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == SKIPPED;
    }
}
