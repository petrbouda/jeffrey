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

package cafe.jeffrey.hub.core.mcp;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.ScopedReplaySource;
import cafe.jeffrey.hub.core.streaming.StreamingWindow;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Instant;

@Configuration
@ConditionalOnProperty(name = "jeffrey.hub.mcp.enabled", havingValue = "true")
public class HubMcpConfiguration {
    @Bean(destroyMethod = "close")
    public HubActivityService hubActivityService(HubPlatformRepositories repositories,
                                                RepositoryStorage.Factory storage, HubJeffreyDirs dirs) {
        var source = new ScopedReplaySource(repositories, storage, dirs);
        return new HubActivityService(request -> source.resolve(request.workspaceId(), request.projectId(), request.sessionId(),
                request.eventTypes(), new StreamingWindow(Instant.ofEpochMilli(request.startTime()), Instant.ofEpochMilli(request.endTime()))));
    }
}
