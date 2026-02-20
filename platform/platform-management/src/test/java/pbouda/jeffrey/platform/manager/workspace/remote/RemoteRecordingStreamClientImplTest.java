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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestBodySpec;
import org.springframework.web.client.RestClient.RequestBodyUriSpec;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClient.ResponseSpec;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyUnavailableException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

@ExtendWith(MockitoExtension.class)
class RemoteRecordingStreamClientImplTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RequestBodySpec requestBodySpec;
    @Mock
    private ResponseSpec responseSpec;

    private RemoteRecordingStreamClientImpl client;

    @BeforeEach
    void setUp() {
        RemoteHttpInvoker invoker = new RemoteHttpInvoker(REMOTE_URI, restClient);
        client = new RemoteRecordingStreamClientImpl(invoker);
    }

    private void stubPostChainForRetrieve() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    private void stubPostChainForExchange() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
    }

    @Nested
    class DownloadRecordings {

        @Test
        void returnsResource_onSuccess() {
            stubPostChainForRetrieve();
            Resource expectedResource = new ByteArrayResource("jfr-data".getBytes());
            when(responseSpec.toEntity(Resource.class))
                    .thenReturn(ResponseEntity.ok(expectedResource));

            CompletableFuture<Resource> future = client.downloadRecordings(
                    WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1", "rec-2"));

            Resource result = future.join();
            assertNotNull(result);
            assertEquals(expectedResource, result);
        }

        @Test
        void throwsRemoteJeffreyUnavailable_onConnectionError() {
            stubPostChainForRetrieve();
            when(responseSpec.toEntity(Resource.class))
                    .thenThrow(new ResourceAccessException("Connection refused"));

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
            stubPostChainForRetrieve();
            Resource expectedResource = new ByteArrayResource("heap-dump".getBytes());
            when(responseSpec.toEntity(Resource.class))
                    .thenReturn(ResponseEntity.ok(expectedResource));

            CompletableFuture<Resource> future = client.downloadFile(
                    WORKSPACE_ID, PROJECT_ID, SESSION_ID, "file-1");

            Resource result = future.join();
            assertNotNull(result);
            assertEquals(expectedResource, result);
        }
    }

    @Nested
    class StreamRecordings {

        @Test
        @SuppressWarnings("unchecked")
        void invokesConsumer_withInputStream_onSuccess() {
            stubPostChainForExchange();

            byte[] data = "streaming-jfr-data".getBytes();
            when(requestBodySpec.exchange(any(RestClient.RequestHeadersSpec.ExchangeFunction.class)))
                    .thenAnswer(invocation -> {
                        RestClient.RequestHeadersSpec.ExchangeFunction<Integer> exchangeFn =
                                invocation.getArgument(0);

                        ConvertibleClientHttpResponse mockResponse = mock(ConvertibleClientHttpResponse.class);
                        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.OK);
                        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(data));
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentLength(data.length);
                        when(mockResponse.getHeaders()).thenReturn(headers);

                        return exchangeFn.exchange(mock(HttpRequest.class), mockResponse);
                    });

            AtomicReference<InputStream> capturedStream = new AtomicReference<>();
            AtomicReference<Long> capturedLength = new AtomicReference<>();

            client.streamRecordings(WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1"),
                    (inputStream, contentLength) -> {
                        capturedStream.set(inputStream);
                        capturedLength.set(contentLength);
                    });

            assertNotNull(capturedStream.get());
            assertEquals(data.length, capturedLength.get());
        }

        @Test
        @SuppressWarnings("unchecked")
        void throwsRemoteJeffreyUnavailable_onResourceAccessException() {
            stubPostChainForExchange();
            when(requestBodySpec.exchange(any(RestClient.RequestHeadersSpec.ExchangeFunction.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThrows(RemoteJeffreyUnavailableException.class, () ->
                    client.streamRecordings(WORKSPACE_ID, PROJECT_ID, SESSION_ID, List.of("rec-1"),
                            (inputStream, contentLength) -> {}));
        }
    }

    @Nested
    class StreamFile {

        @Test
        @SuppressWarnings("unchecked")
        void invokesConsumer_withInputStream_onSuccess() {
            stubPostChainForExchange();

            byte[] data = "streaming-heap-dump".getBytes();
            when(requestBodySpec.exchange(any(RestClient.RequestHeadersSpec.ExchangeFunction.class)))
                    .thenAnswer(invocation -> {
                        RestClient.RequestHeadersSpec.ExchangeFunction<Integer> exchangeFn =
                                invocation.getArgument(0);

                        ConvertibleClientHttpResponse mockResponse = mock(ConvertibleClientHttpResponse.class);
                        when(mockResponse.getStatusCode()).thenReturn(HttpStatus.OK);
                        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(data));
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentLength(data.length);
                        when(mockResponse.getHeaders()).thenReturn(headers);

                        return exchangeFn.exchange(mock(HttpRequest.class), mockResponse);
                    });

            AtomicReference<InputStream> capturedStream = new AtomicReference<>();
            AtomicReference<Long> capturedLength = new AtomicReference<>();

            client.streamFile(WORKSPACE_ID, PROJECT_ID, SESSION_ID, "file-1",
                    (inputStream, contentLength) -> {
                        capturedStream.set(inputStream);
                        capturedLength.set(contentLength);
                    });

            assertNotNull(capturedStream.get());
            assertEquals(data.length, capturedLength.get());
        }

        @Test
        @SuppressWarnings("unchecked")
        void throwsRemoteJeffreyUnavailable_onResourceAccessException() {
            stubPostChainForExchange();
            when(requestBodySpec.exchange(any(RestClient.RequestHeadersSpec.ExchangeFunction.class)))
                    .thenThrow(new ResourceAccessException("Connection refused"));

            assertThrows(RemoteJeffreyUnavailableException.class, () ->
                    client.streamFile(WORKSPACE_ID, PROJECT_ID, SESSION_ID, "file-1",
                            (inputStream, contentLength) -> {}));
        }
    }
}
