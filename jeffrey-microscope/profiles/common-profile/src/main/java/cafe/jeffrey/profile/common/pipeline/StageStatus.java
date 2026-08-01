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
