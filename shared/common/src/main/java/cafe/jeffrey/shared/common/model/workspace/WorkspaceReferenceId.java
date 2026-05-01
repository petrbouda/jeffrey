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

package cafe.jeffrey.shared.common.model.workspace;

import java.util.regex.Pattern;

public final class WorkspaceReferenceId {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 64;

    public static final Pattern PATTERN = Pattern.compile(
            "^(\\$[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9])$");

    public static final String DESCRIPTION =
            "must be 3-64 characters, alphanumeric and dashes only, no leading or trailing dash; "
                    + "may optionally start with '$' to denote a system-reserved workspace";

    private WorkspaceReferenceId() {
    }

    public static boolean isValid(String referenceId) {
        return referenceId != null && PATTERN.matcher(referenceId).matches();
    }

    public static boolean isSystem(String referenceId) {
        return referenceId != null && referenceId.startsWith("$");
    }

    public static void validate(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("Workspace reference ID is required");
        }
        if (!PATTERN.matcher(referenceId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid workspace reference ID '" + referenceId + "': " + DESCRIPTION);
        }
    }
}
