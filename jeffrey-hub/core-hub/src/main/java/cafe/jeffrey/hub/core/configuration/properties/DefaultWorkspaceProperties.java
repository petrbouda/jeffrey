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

package cafe.jeffrey.hub.core.configuration.properties;

import cafe.jeffrey.shared.common.CliConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jeffrey.hub.default-workspace.*}: the workspace every hub starts with. Bound by Boot,
 * which is why it is a mutable bean; the two-argument constructor is for tests.
 */
@ConfigurationProperties("jeffrey.hub.default-workspace")
public class DefaultWorkspaceProperties {

    private String referenceId = CliConstants.DEFAULT_WORKSPACE_REF_ID;
    private String name = CliConstants.DEFAULT_WORKSPACE_REF_ID;

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
                    "jeffrey.hub.default-workspace.reference-id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "jeffrey.hub.default-workspace.name must not be blank");
        }
    }
}
