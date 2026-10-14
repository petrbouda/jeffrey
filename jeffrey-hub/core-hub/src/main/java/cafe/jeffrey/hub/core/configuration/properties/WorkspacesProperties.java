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
 * volume without the hub's {@code .workspace-info.json} — one the provisioner wrote into but
 * nobody created. Off by default: such a directory is listed only when an operator has said that
 * any workspace the provisioner names is welcome; otherwise CreateWorkspace adopts it.
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
