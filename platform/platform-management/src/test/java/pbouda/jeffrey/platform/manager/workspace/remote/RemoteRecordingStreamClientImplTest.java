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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.TestContext;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteRecordingStreamClientImplTest {

    private static final String RECORDINGS_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/repository/sessions/" + SESSION_ID + "/recordings";
    private static final String ARTIFACT_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/repository/sessions/" + SESSION_ID + "/artifact";

    private RemoteRecordingStreamClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        client = new RemoteRecordingStreamClientImpl(ctx.invoker());
        server = ctx.server();
    }

    @Nested
    class DownloadRecordings {

        @Test
        void returnsResource_onSuccess() {
            byte[] data = "jfr-data".getBytes();
            server.expect(requestTo(RECORDINGS_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(data, MediaType.APPLICATION_OCTET_STREAM));

            CompletableFuture<Resource> future = client.downloadRecordings(
                    WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1", "rec-2"));

            Resource result = future.join();
            assertNotNull(result);
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyUnavailable_onConnectionError() {
            server.expect(requestTo(RECORDINGS_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(request -> {
                        throw new IOException("Connection refused");
                    });

            CompletableFuture<Resource> future = client.downloadRecordings(
                    WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1"));

            CompletionException ex = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(RemoteJeffreyUnavailableException.class, ex.getCause());
        }
    }

    @Nested
    class DownloadFile {

        @Test
        void returnsResource_onSuccess() {
            byte[] data = "heap-dump".getBytes();
            server.expect(requestTo(ARTIFACT_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(data, MediaType.APPLICATION_OCTET_STREAM));

            CompletableFuture<Resource> future = client.downloadFile(
                    WORKSPACE_ID, PROJECT_ID, SESSION_ID, "file-1");

            Resource result = future.join();
            assertNotNull(result);
            server.verify();
        }
    }

    @Nested
    class StreamRecordings {

        @Test
        void invokesConsumer_withInputStream_onSuccess() throws IOException {
            byte[] data = "streaming-jfr-data".getBytes();
            server.expect(requestTo(RECORDINGS_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(data, MediaType.APPLICATION_OCTET_STREAM)
                            .headers(contentLengthHeader(data.length)));

            AtomicReference<byte[]> capturedData = new AtomicReference<>();
            AtomicReference<Long> capturedLength = new AtomicReference<>();

            client.streamRecordings(WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1"),
                    (inputStream, contentLength) -> {
                        capturedData.set(inputStream.readAllBytes());
                        capturedLength.set(contentLength);
                    });

            assertArrayEquals(data, capturedData.get());
            assertEquals(data.length, capturedLength.get());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyUnavailable_onConnectionError() {
            server.expect(requestTo(RECORDINGS_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(request -> {
                        throw new IOException("Connection refused");
                    });

            assertThrows(RemoteJeffreyUnavailableException.class, () ->
                    client.streamRecordings(WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1"),
                            (inputStream, contentLength) -> {}));
        }
    }

    @Nested
    class StreamFile {

        @Test
        void invokesConsumer_withInputStream_onSuccess() throws IOException {
            byte[] data = "streaming-heap-dump".getBytes();
            server.expect(requestTo(ARTIFACT_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(data, MediaType.APPLICATION_OCTET_STREAM)
                            .headers(contentLengthHeader(data.length)));

            AtomicReference<byte[]> capturedData = new AtomicReference<>();
            AtomicReference<Long> capturedLength = new AtomicReference<>();

            client.streamFile(WORKSPACE_ID, PROJECT_ID, SESSION_ID, "file-1",
                    (inputStream, contentLength) -> {
                        capturedData.set(inputStream.readAllBytes());
                        capturedLength.set(contentLength);
                    });

            assertArrayEquals(data, capturedData.get());
            assertEquals(data.length, capturedLength.get());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyUnavailable_onConnectionError() {
            server.expect(requestTo(ARTIFACT_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(request -> {
                        throw new IOException("Connection refused");
                    });

            assertThrows(RemoteJeffreyUnavailableException.class, () ->
                    client.streamFile(WORKSPACE_ID, PROJECT_ID, SESSION_ID, "file-1",
                            (inputStream, contentLength) -> {}));
        }
    }

    private static org.springframework.http.HttpHeaders contentLengthHeader(long length) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentLength(length);
        return headers;
    }
}
