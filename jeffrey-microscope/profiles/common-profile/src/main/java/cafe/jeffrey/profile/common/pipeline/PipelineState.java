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
