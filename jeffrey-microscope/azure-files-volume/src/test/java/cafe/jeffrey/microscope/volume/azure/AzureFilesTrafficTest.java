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

package cafe.jeffrey.microscope.volume.azure;

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.shared.volume.VolumeAccess;
import cafe.jeffrey.shared.volume.VolumePath;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.util.Context;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every request the volume sends to the share is seen once it ends, as one exchange: its method,
 * operation, path and status, how long it took and the declared sizes — never the query that
 * carries the SAS signature.
 */
class AzureFilesTrafficTest {

    private static final Instant CREATED = Instant.parse("2026-02-20T12:00:00Z");
    private static final String ENDPOINT = "https://acct.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=c2lnbmF0dXJl";
    private static final String CHUNK = "chunk one";
    private static final VolumePath SESSION = VolumePath.root()
            .resolve("prod").resolve("orders").resolve("pod-a").resolve("s-1");

    private final List<AzureFilesExchange> exchanges = new CopyOnWriteArrayList<>();
    private final AzureFilesTraffic traffic = new AzureFilesTraffic(StubFileShare.shareName(), exchanges::add);

    private static AzureFilesExchange.Answered answered(AzureFilesExchange exchange) {
        return assertInstanceOf(AzureFilesExchange.Answered.class, exchange.outcome());
    }

    @Nested
    class ThroughTheVolume {

        private final StubFileShare share = new StubFileShare(AzureFilesProtocol.SMB)
                .file("prod/orders/pod-a/s-1/rec-20260220-120000.jfr", CHUNK, CREATED);
        private final VolumeAccess volume = AzureFilesVolumes.open(
                new AzureFilesAddress(ENDPOINT, StubFileShare.shareName(), "", AzureFilesProtocol.SMB,
                        AzureFilesAuth.SAS, false),
                new AzureFilesCredential.Sas(SAS), share, traffic);

        @Test
        void aListingIsOneExchange() {
            volume.list(SESSION);

            assertEquals(1, exchanges.size());
            AzureFilesExchange exchange = exchanges.getFirst();
            assertEquals(StubFileShare.shareName(), exchange.share());
            assertEquals(1, exchange.number());
            assertEquals("GET", exchange.request().method());
            assertEquals("list", exchange.request().operation());
            assertEquals("/recordings/prod/orders/pod-a/s-1", exchange.request().path());
            assertEquals(200, answered(exchange).status());
            assertFalse(answered(exchange).elapsed().isNegative());
        }

        /** Not a file by that name, then not a directory either: two requests, both answered 404. */
        @Test
        void aLookAtAMissingEntryIsTwoExchangesNumberedInOrder() {
            volume.stat(SESSION.resolve("missing.jfr"));

            assertEquals(List.of("file-properties", "directory-properties"),
                    exchanges.stream().map(exchange -> exchange.request().operation()).toList());
            assertEquals(List.of(404, 404), exchanges.stream().map(exchange -> answered(exchange).status()).toList());
            assertEquals(List.of(1L, 2L), exchanges.stream().map(AzureFilesExchange::number).toList());
        }

        @Test
        void aDownloadDeclaresTheBytesItCarries() throws IOException {
            try (VolumeAccess.OpenedFile chunk = volume.open(SESSION.resolve("rec-20260220-120000.jfr")).orElseThrow()) {
                chunk.content().readAllBytes();
            }

            AzureFilesExchange download = exchanges.stream()
                    .filter(exchange -> exchange.request().operation().equals("download"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(CHUNK.length(), answered(download).responseBytes());
        }

        @Test
        void neverKeepsTheSignature() {
            volume.list(SESSION);
            volume.stat(SESSION);

            assertFalse(exchanges.isEmpty());
            assertFalse(exchanges.stream().anyMatch(exchange -> exchange.toString().contains("sig=")),
                    exchanges.toString());
        }
    }

    /** A request that gets no response, sent through a bare pipeline: the volume's retries would only wait. */
    @Nested
    class WithNoResponse {

        private final ConnectException refusal = new ConnectException("refused https://acct/recordings?sig=secret");
        private final HttpClient refusing = request -> Mono.error(refusal);
        private final HttpPipeline pipeline = new HttpPipelineBuilder()
                .policies(traffic)
                .httpClient(refusing)
                .build();
        private final HttpRequest request = new HttpRequest(HttpMethod.HEAD, ENDPOINT + "/recordings/gc.log?" + SAS);

        @Test
        void aSyncRequestIsOneFailedExchangeAndStillFails() {
            assertThrows(RuntimeException.class, () -> pipeline.sendSync(request, Context.NONE));

            assertFailed();
        }

        @Test
        void anAsyncRequestIsOneFailedExchangeAndStillFails() {
            assertThrows(RuntimeException.class, () -> pipeline.send(request).block());

            assertFailed();
        }

        private void assertFailed() {
            assertEquals(1, exchanges.size());
            AzureFilesExchange exchange = exchanges.getFirst();
            assertEquals("file-properties", exchange.request().operation());
            assertEquals("/recordings/gc.log", exchange.request().path());
            AzureFilesExchange.Failed failed = assertInstanceOf(AzureFilesExchange.Failed.class, exchange.outcome());
            assertSame(refusal, failed.rootCause());
        }
    }

    @Test
    void theRootCauseIsTheInnermostError() {
        ConnectException refusal = new ConnectException("refused");
        AzureFilesExchange.Failed failed = new AzureFilesExchange.Failed(
                new UncheckedIOException(new IOException(refusal)), Duration.ZERO);

        assertSame(refusal, failed.rootCause());
    }
}
