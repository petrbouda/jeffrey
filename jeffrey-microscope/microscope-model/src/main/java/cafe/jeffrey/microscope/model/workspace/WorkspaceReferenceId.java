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

package cafe.jeffrey.microscope.model.workspace;

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
