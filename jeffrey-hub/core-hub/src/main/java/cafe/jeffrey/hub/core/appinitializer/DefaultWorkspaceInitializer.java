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

package cafe.jeffrey.hub.core.appinitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;

public class DefaultWorkspaceInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWorkspaceInitializer.class);

    private final WorkspacesManager workspacesManager;
    private final DefaultWorkspaceProperties properties;

    public DefaultWorkspaceInitializer(
            WorkspacesManager workspacesManager,
            DefaultWorkspaceProperties properties) {

        this.workspacesManager = workspacesManager;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        run();
    }

    public void run() {
        properties.validate();

        String referenceId = properties.getReferenceId();
        String name = properties.getName();

        if (workspacesManager.findByReferenceId(referenceId).isPresent()) {
            LOG.debug("Default workspace already present, skipping creation: reference_id={}", referenceId);
            return;
        }

        WorkspaceInfo created = workspacesManager.create(
                new WorkspacesManager.CreateWorkspaceRequest(referenceId, name));

        LOG.info("Created default workspace: workspace_id={} reference_id={} name={}",
                created.id(), created.referenceId(), created.name());
    }
}
