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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.api.v1.ListSessionsRequest;
import cafe.jeffrey.hub.api.v1.ListSessionsResponse;
import cafe.jeffrey.hub.api.v1.RecordingSession;
import cafe.jeffrey.hub.api.v1.RecordingStatus;
import cafe.jeffrey.hub.api.v1.RepositoryServiceGrpc;
import cafe.jeffrey.hub.api.v1.SessionFilter;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryClientTest {

    private static final String PROJECT_ID = "proj-1";
    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

    /**
     * Records the last request so a test can assert what the client put on the wire, and answers
     * with one canned session so the response mapping is exercised too.
     */
    private static final class CapturingRepositoryService extends RepositoryServiceGrpc.RepositoryServiceImplBase {

        private final AtomicReference<ListSessionsRequest> lastRequest = new AtomicReference<>();

        @Override
        public void listSessions(ListSessionsRequest request, StreamObserver<ListSessionsResponse> observer) {
            lastRequest.set(request);
            observer.onNext(ListSessionsResponse.newBuilder()
                    .addSessions(RecordingSession.newBuilder()
                            .setId("session-1")
                            .setName("session-1")
                            .setCreatedAt(NOW.toEpochMilli())
                            .setStatus(RecordingStatus.RECORDING_STATUS_ACTIVE))
                    .build());
            observer.onCompleted();
        }
    }

    private final CapturingRepositoryService service = new CapturingRepositoryService();

    private Server server;
    private ManagedChannel channel;
    private RepositoryClient client;

    @BeforeEach
    void start() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new RepositoryClient(new GrpcHubConnection(channel) { });
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Nested
    class RecordingSessions {

        @Test
        void unfilteredListingSendsAnEmptyFilter() {
            List<RecordingSessionResponse> sessions = client.recordingSessions(PROJECT_ID);

            ListSessionsRequest sent = service.lastRequest.get();
            assertEquals(PROJECT_ID, sent.getProjectId());
            assertEquals(SessionFilter.getDefaultInstance(), sent.getFilter());
            assertEquals(1, sessions.size());
            assertEquals("session-1", sessions.getFirst().id());
        }

        @Test
        void filterBoundsStatusAndLimitTravelToTheHub() {
            Instant from = NOW.minus(Duration.ofHours(1));
            var filter = new RecordingSessionFilter(
                    from, NOW, cafe.jeffrey.shared.common.model.repository.RecordingStatus.FINISHED, 3);

            client.recordingSessions(PROJECT_ID, filter);

            SessionFilter sent = service.lastRequest.get().getFilter();
            assertTrue(sent.hasActiveFrom());
            assertEquals(from.toEpochMilli(), sent.getActiveFrom());
            assertTrue(sent.hasActiveTo());
            assertEquals(NOW.toEpochMilli(), sent.getActiveTo());
            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, sent.getStatus());
            assertEquals(3, sent.getLimit());
        }

        @Test
        void openBoundsStayUnsetOnTheWire() {
            client.recordingSessions(PROJECT_ID, RecordingSessionFilter.activeWithinLast(Duration.ofHours(1), NOW));

            SessionFilter sent = service.lastRequest.get().getFilter();
            assertTrue(sent.hasActiveFrom());
            assertFalse(sent.hasActiveTo());
            assertEquals(RecordingStatus.RECORDING_STATUS_UNSPECIFIED, sent.getStatus());
            assertEquals(0, sent.getLimit());
        }
    }
}
