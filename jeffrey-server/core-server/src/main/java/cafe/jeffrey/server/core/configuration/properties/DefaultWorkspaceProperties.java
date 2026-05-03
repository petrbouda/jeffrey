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

package cafe.jeffrey.server.core.configuration.properties;

import cafe.jeffrey.shared.common.CliConstants;

public class DefaultWorkspaceProperties {

    private String referenceId = CliConstants.DEFAULT_WORKSPACE_REF_ID;
    private String name = "$default";

    public DefaultWorkspaceProperties() {
    }

    public DefaultWorkspaceProperties(String referenceId, String name) {
        this.referenceId = referenceId;
        this.name = name;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void validate() {
        if (referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException(
                    "jeffrey.server.default-workspace.reference-id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "jeffrey.server.default-workspace.name must not be blank");
        }
    }
}
