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

package pbouda.jeffrey.local.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.BlockProjectRequest;
import pbouda.jeffrey.server.api.v1.DeleteProjectRequest;
import pbouda.jeffrey.server.api.v1.ProjectServiceGrpc;
import pbouda.jeffrey.server.api.v1.UnblockProjectRequest;

public class RemoteProjectsClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsClient.class);

    private final ProjectServiceGrpc.ProjectServiceBlockingStub stub;

    public RemoteProjectsClient(GrpcServerConnection connection) {
        this.stub = ProjectServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public void deleteProject(String workspaceId, String projectId) {
        stub.deleteProject(DeleteProjectRequest.newBuilder()
                .setWorkspaceId(workspaceId)
                .setProjectId(projectId)
                .build());

        LOG.debug("Deleted project via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

    public void blockProject(String workspaceId, String projectId) {
        stub.blockProject(BlockProjectRequest.newBuilder()
                .setWorkspaceId(workspaceId)
                .setProjectId(projectId)
                .build());

        LOG.debug("Blocked project via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }

    public void unblockProject(String workspaceId, String projectId) {
        stub.unblockProject(UnblockProjectRequest.newBuilder()
                .setWorkspaceId(workspaceId)
                .setProjectId(projectId)
                .build());

        LOG.debug("Unblocked project via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
    }
}
