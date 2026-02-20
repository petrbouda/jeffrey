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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import pbouda.jeffrey.platform.resources.pub.PublicApiPaths;
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;

import java.util.List;

public class RemoteMessagesClientImpl implements RemoteMessagesClient {

    private static final ParameterizedTypeReference<List<ImportantMessageResponse>> MESSAGE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RemoteHttpInvoker invoker;

    public RemoteMessagesClientImpl(RemoteHttpInvoker invoker) {
        this.invoker = invoker;
    }

    @Override
    public List<ImportantMessageResponse> getMessages(String workspaceId, String projectId, Long start, Long end) {
        ResponseEntity<List<ImportantMessageResponse>> messages = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PublicApiPaths.MESSAGES)
                            .queryParamIfPresent("start", java.util.Optional.ofNullable(start))
                            .queryParamIfPresent("end", java.util.Optional.ofNullable(end))
                            .build(workspaceId, projectId))
                    .retrieve()
                    .toEntity(MESSAGE_LIST_TYPE);
        });

        return messages.getBody() != null ? messages.getBody() : List.of();
    }

    @Override
    public List<ImportantMessageResponse> getAlerts(String workspaceId, String projectId, Long start, Long end) {
        ResponseEntity<List<ImportantMessageResponse>> alerts = invoker.get(() -> {
            return invoker.restClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path(PublicApiPaths.ALERTS)
                            .queryParamIfPresent("start", java.util.Optional.ofNullable(start))
                            .queryParamIfPresent("end", java.util.Optional.ofNullable(end))
                            .build(workspaceId, projectId))
                    .retrieve()
                    .toEntity(MESSAGE_LIST_TYPE);
        });

        return alerts.getBody() != null ? alerts.getBody() : List.of();
    }
}
