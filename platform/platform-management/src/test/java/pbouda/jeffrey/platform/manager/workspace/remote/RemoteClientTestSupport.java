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

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pbouda.jeffrey.platform.resources.response.InstanceResponse;
import pbouda.jeffrey.platform.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.platform.resources.response.*;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.net.URI;
import java.util.List;

final class RemoteClientTestSupport {

    static final URI REMOTE_URI = URI.create("http://remote-jeffrey:8080");
    static final String WORKSPACE_ID = "ws-1";
    static final String PROJECT_ID = "proj-1";
    static final String SESSION_ID = "session-1";
    static final String INSTANCE_ID = "inst-1";

    record TestContext(RemoteHttpInvoker invoker, MockRestServiceServer server) {
    }

    static TestContext createInvokerAndServer() {
        RestClient.Builder builder = RestClient.builder().baseUrl(REMOTE_URI.toString());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RemoteHttpInvoker invoker = new RemoteHttpInvoker(REMOTE_URI, restClient);
        return new TestContext(invoker, server);
    }

    static String errorJson(ErrorType type, ErrorCode code, String message) {
        return """
                {"type":"%s","code":"%s","message":"%s"}""".formatted(type.name(), code.name(), message);
    }

    static WorkspaceResponse sampleWorkspaceResponse() {
        return new WorkspaceResponse(
                WORKSPACE_ID, "Test Workspace", "A test workspace",
                1700000000000L, 3, WorkspaceStatus.AVAILABLE, WorkspaceType.SANDBOX);
    }

    static ProjectResponse sampleProjectResponse() {
        return new ProjectResponse(
                PROJECT_ID, "origin-1", "Test Project", "test-project", "default",
                "2025-06-01T12:00:00Z", WORKSPACE_ID, WorkspaceType.SANDBOX,
                RecordingStatus.FINISHED, 5, 10, 2, 1, 0,
                RecordingEventSource.JDK, false, false);
    }

    static RecordingSessionResponse sampleSessionResponse() {
        return new RecordingSessionResponse(
                SESSION_ID, "session-one", INSTANCE_ID,
                1700000000000L, 1700003600000L, 1700003000000L,
                RecordingStatus.FINISHED, "cpu=true",
                List.of(sampleFileResponse()));
    }

    static RepositoryFileResponse sampleFileResponse() {
        return new RepositoryFileResponse(
                "file-1", "recording.jfr", 1700000000000L, 1024L,
                SupportedRecordingFile.JFR, true, RecordingStatus.FINISHED);
    }

    static RepositoryStatisticsResponse sampleStatisticsResponse() {
        return new RepositoryStatisticsResponse(
                2, RecordingStatus.FINISHED, 1700003600000L,
                2048L, 3, 1500L, 2, 0, 1, 0, 0);
    }

    static ProfilerSettingsResponse sampleProfilerSettingsResponse() {
        return new ProfilerSettingsResponse("cpu=true,alloc=true", SettingsLevel.PROJECT);
    }

    static ImportantMessageResponse sampleMessageResponse() {
        return new ImportantMessageResponse(
                "MESSAGE", "Test Title", "Test message body",
                "INFO", "system", "test-source",
                false, SESSION_ID, "2025-06-01T12:00:00Z");
    }

    static ImportantMessageResponse sampleAlertResponse() {
        return new ImportantMessageResponse(
                "ALERT", "Alert Title", "Alert message body",
                "WARNING", "profiler", "profiler-source",
                true, SESSION_ID, "2025-06-01T13:00:00Z");
    }

    static InstanceResponse sampleInstanceResponse() {
        return new InstanceResponse(
                INSTANCE_ID, "host-1", "ACTIVE",
                1700000000000L, null, 2, SESSION_ID);
    }

    static InstanceSessionResponse sampleInstanceSessionResponse() {
        return new InstanceSessionResponse(
                SESSION_ID, "repo-1",
                1700000000000L, null, 1700003000000L, true);
    }

    private RemoteClientTestSupport() {
    }
}
