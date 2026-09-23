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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.DeleteProjectRequest;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.RestoreProjectRequest;

public class ProjectsClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsClient.class);

    private final ProjectServiceGrpc.ProjectServiceBlockingStub stub;

    public ProjectsClient(GrpcHubConnection connection) {
        this.stub = ProjectServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public void deleteProject(String projectId) {
        stub.deleteProject(DeleteProjectRequest.newBuilder()
                .setProjectId(projectId)
                .build());

        LOG.debug("Deleted project via gRPC: projectId={}", projectId);
    }

    public void restoreProject(String projectId) {
        stub.restoreProject(RestoreProjectRequest.newBuilder()
                .setProjectId(projectId)
                .build());

        LOG.debug("Restored project via gRPC: projectId={}", projectId);
    }

}
