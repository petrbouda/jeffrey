/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.configuration.workspace;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import pbouda.jeffrey.configuration.AppConfiguration;
import pbouda.jeffrey.manager.workspace.CompositeWorkspacesManager;
import pbouda.jeffrey.manager.workspace.LocalWorkspacesManager;
import pbouda.jeffrey.manager.workspace.RemoteWorkspacesManager;
import pbouda.jeffrey.manager.workspace.SandboxWorkspacesManager;
import pbouda.jeffrey.provider.api.repository.Repositories;

@Configuration
@Import(AppConfiguration.class)
public class WorkspaceConfiguration {

    @Bean
    public CompositeWorkspacesManager compositeWorkspacesManager(
            Repositories repositories,
            SandboxWorkspacesManager sandboxWorkspacesManager,
            LocalWorkspacesManager localWorkspacesManager,
            RemoteWorkspacesManager remoteWorkspacesManager) {

        return new CompositeWorkspacesManager(
                repositories.newWorkspacesRepository(),
                sandboxWorkspacesManager,
                remoteWorkspacesManager,
                localWorkspacesManager);
    }
}
