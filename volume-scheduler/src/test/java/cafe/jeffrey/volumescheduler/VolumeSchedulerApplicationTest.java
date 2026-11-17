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

package cafe.jeffrey.volumescheduler;

import cafe.jeffrey.test.volume.VolumeWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole service booted over a volume on disk, driven over HTTP as an operator drives it: the
 * jobs it lists, a run on demand that changes the volume, the storage figures that run publishes,
 * and the probes Kubernetes asks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VolumeSchedulerApplicationTest {

    private static final Instant T0 = Instant.parse("2026-02-20T12:00:00Z");
    private static final String CHUNK = "a chunk of events, repeated so the frame compresses".repeat(20);

    @TempDir
    static Path volume;

    private static Path session;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void volume(DynamicPropertyRegistry registry) {
        registry.add("jeffrey.volume-scheduler.volume.path", () -> volume.toString());
        // Nothing runs on its own here: every run is the test's
        registry.add("jeffrey.volume-scheduler.retention.enabled", () -> "false");
        registry.add("jeffrey.volume-scheduler.compression.enabled", () -> "false");
        // Windows wider than the recordings' age, so a retention run keeps whatever another test laid down
        registry.add("jeffrey.volume-scheduler.retention.session-retention", () -> "36500d");
        registry.add("jeffrey.volume-scheduler.retention.newest-session-file-retention", () -> "36500d");
        registry.add("jeffrey.volume-scheduler.retention.instance-retention", () -> "36500d");
    }

    /** One ended session with two closed chunks, under {@code <volume>/workspaces/ws/orders/pod-a}. */
    @BeforeAll
    static void layDownTheVolume() {
        Path instance = VolumeWriter.instance(
                VolumeWriter.project(VolumeWriter.workspace(volume.resolve("workspaces"), "ws"), "orders"), "pod-a");
        session = VolumeWriter.session(instance, T0);
        VolumeWriter.recording(session, T0.plusSeconds(60), ZoneOffset.UTC, CHUNK);
        VolumeWriter.recording(session, T0.plusSeconds(120), ZoneOffset.UTC, CHUNK);
        VolumeWriter.session(instance, T0.plusSeconds(1800));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void listsBothJobs() throws Exception {
        HttpResponse<String> jobs = get("/api/v1/jobs");

        assertThat(jobs.statusCode()).isEqualTo(200);
        assertThat(jobs.body()).contains("\"id\":\"retention\"", "\"id\":\"compression\"", "\"maxSizeBytes\":21474836480");
    }

    @Test
    void aCompressionRunOnDemandCompressesTheEndedSession() throws Exception {
        HttpResponse<String> run = post("/api/v1/jobs/compression/run");

        assertThat(run.statusCode()).isEqualTo(200);
        assertThat(run.body()).contains("\"job\":\"compression\"",
                "\"summary\":\"Compressed 2 recordings in 1 session across 1 project\"");
        assertThat(names(session)).allSatisfy(name -> assertThat(name).endsWith(".jfr.lz4")).hasSize(2);
    }

    @Test
    void aRetentionRunOnDemandPublishesTheStorageFigures() throws Exception {
        HttpResponse<String> run = post("/api/v1/jobs/retention/run");
        HttpResponse<String> storage = get("/api/v1/storage");

        assertThat(run.statusCode()).isEqualTo(200);
        assertThat(run.body()).contains("\"summary\":\"Deleted 0 sessions");
        assertThat(storage.statusCode()).isEqualTo(200);
        assertThat(storage.body()).contains("\"id\":\"ws\"", "\"id\":\"orders\"", "\"measuredAt\":\"");
    }

    @Test
    void anUnknownJobIsNotFound() throws Exception {
        HttpResponse<String> job = get("/api/v1/jobs/cleanup");

        assertThat(job.statusCode()).isEqualTo(404);
        assertThat(job.body()).contains("\"code\":\"JOB_NOT_FOUND\"");
    }

    @Test
    void answersTheKubernetesProbesOutsideKubernetes() throws Exception {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void writesNothingOfItsOwnOntoTheVolume() throws IOException {
        assertThat(names(volume)).containsExactly("workspaces");
    }
}
