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

import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpPipelineNextSyncPolicy;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Every request sent to one share, logged at {@code INFO} in one line once it ends — retries
 * included, each its own line — with no payload, no header and no query string (which carries
 * the SAS signature):
 *
 * <pre>
 * Azure Files request: share=recordings method=GET operation=list path=/recordings/prod status=200 elapsed_ms=31 request_bytes=0 response_bytes=2311
 * Azure Files request failed: share=recordings method=HEAD operation=file-properties path=/recordings/prod/gc.log elapsed_ms=30001 error=java.io.UncheckedIOException cause=java.net.http.HttpTimeoutException
 * </pre>
 *
 * <p>Silenced by
 * {@code logging.level.cafe.jeffrey.microscope.volume.azure.AzureFilesTraffic=WARN}.
 */
final class AzureFilesTraffic implements HttpPipelinePolicy {

    private static final Logger LOG = LoggerFactory.getLogger(AzureFilesTraffic.class);

    private final String share;
    private final Consumer<AzureFilesExchange> listener;

    /**
     * @param share the share's name, which every line carries
     */
    AzureFilesTraffic(String share) {
        this(share, AzureFilesTraffic::log);
    }

    /**
     * @param share    the share's name
     * @param listener what is handed each exchange once it ends, in place of the log
     */
    AzureFilesTraffic(String share, Consumer<AzureFilesExchange> listener) {
        this.share = share;
        this.listener = listener;
    }

    @Override
    public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
        AzureFilesExchange.Request request = AzureFilesExchange.Request.of(context.getHttpRequest());
        // Materialized, so a failure is timed as a response is
        return next.process()
                .materialize()
                .elapsed()
                .map(timed -> {
                    Duration elapsed = Duration.ofMillis(timed.getT1());
                    Signal<HttpResponse> signal = timed.getT2();
                    if (signal.hasValue()) {
                        ended(request, answered(signal.get(), elapsed));
                    } else if (signal.isOnError()) {
                        ended(request, new AzureFilesExchange.Failed(signal.getThrowable(), elapsed));
                    }
                    return signal;
                })
                .dematerialize();
    }

    @Override
    public HttpResponse processSync(HttpPipelineCallContext context, HttpPipelineNextSyncPolicy next) {
        AzureFilesExchange.Request request = AzureFilesExchange.Request.of(context.getHttpRequest());
        Elapsed<Returned> elapsed = Measuring.s(() -> {
            try {
                return new Returned(next.processSync(), null);
            } catch (RuntimeException e) {
                return new Returned(null, e);
            }
        });
        Returned returned = elapsed.entity();
        if (returned.error() != null) {
            ended(request, new AzureFilesExchange.Failed(returned.error(), elapsed.duration()));
            throw returned.error();
        }
        ended(request, answered(returned.response(), elapsed.duration()));
        return returned.response();
    }

    /** What the next policy came back with: a response, or what it threw. */
    private record Returned(HttpResponse response, RuntimeException error) {
    }

    private static AzureFilesExchange.Answered answered(HttpResponse response, Duration elapsed) {
        return new AzureFilesExchange.Answered(
                response.getStatusCode(), AzureFilesExchange.contentLength(response.getHeaders()), elapsed);
    }

    private void ended(AzureFilesExchange.Request request, AzureFilesExchange.Outcome outcome) {
        listener.accept(new AzureFilesExchange(share, request, outcome));
    }

    private static void log(AzureFilesExchange exchange) {
        if (!LOG.isInfoEnabled()) {
            return;
        }
        AzureFilesExchange.Request request = exchange.request();
        switch (exchange.outcome()) {
            case AzureFilesExchange.Answered answered -> LOG.info(
                    "Azure Files request: share={} method={} operation={} path={} status={} "
                            + "elapsed_ms={} request_bytes={} response_bytes={}",
                    exchange.share(), request.method(), request.operation(), request.path(),
                    answered.status(), answered.elapsed().toMillis(), request.requestBytes(),
                    answered.responseBytes());
            // The types alone, never a message: a transport's may quote the URL, signature and all
            case AzureFilesExchange.Failed failed -> LOG.info(
                    "Azure Files request failed: share={} method={} operation={} path={} "
                            + "elapsed_ms={} error={} cause={}",
                    exchange.share(), request.method(), request.operation(), request.path(),
                    failed.elapsed().toMillis(), failed.error().getClass().getName(),
                    failed.rootCause().getClass().getName());
        }
    }
}
