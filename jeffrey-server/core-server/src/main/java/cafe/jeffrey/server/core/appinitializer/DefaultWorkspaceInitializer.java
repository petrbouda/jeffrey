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

package cafe.jeffrey.server.core.appinitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import cafe.jeffrey.server.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

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
                WorkspacesManager.CreateWorkspaceRequest.builder()
                        .referenceId(referenceId)
                        .name(name)
                        .build());

        LOG.info("Created default workspace: workspace_id={} reference_id={} name={}",
                created.id(), created.referenceId(), created.name());
    }
}
