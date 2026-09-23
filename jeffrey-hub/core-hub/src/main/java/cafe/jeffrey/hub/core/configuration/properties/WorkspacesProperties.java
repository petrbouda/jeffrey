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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jeffrey.hub.workspaces.*}: how the hub treats a workspace directory it finds on the
 * volume with no workspace row behind it. Off by default — a directory is created by the
 * reconciler only when an operator has said that any announced workspace is welcome.
 */
@ConfigurationProperties("jeffrey.hub.workspaces")
public class WorkspacesProperties {

    private boolean autoCreate = false;

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }
}
