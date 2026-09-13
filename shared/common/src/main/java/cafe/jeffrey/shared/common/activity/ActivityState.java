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

package cafe.jeffrey.shared.common.activity;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle of one scan. A cancellation request is not yet a terminal outcome. */
public enum ActivityState {

    QUEUED("queued"),
    RUNNING("running"),
    CANCEL_REQUESTED("cancel_requested"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    FAILED("failed");

    private final String code;

    ActivityState(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
