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

package pbouda.jeffrey.manual;

import pbouda.jeffrey.local.core.client.GrpcServerConnection;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.local.persistence.model.WorkspaceAddress;
import pbouda.jeffrey.server.api.v1.StreamingEvent;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public final class EventStreamingProbe {

    private static final String host = "jeffrey-grpc.green.commodities.gwc.uat.second-foundation.azsfint";
    private static final int port = 443;
    private static final String sessionId = "019d6996-28d4-72f5-b884-97390e64c5ce";
    private static final boolean continuous = false;
    private static final Set<String> eventTypes = Set.of("jdk.CPULoad");
    private static final Long startTime = 0L;  // from beginning
    private static final Long endTime = Instant.now().toEpochMilli();  // let the server apply its default (now, if bounded)

    static void main() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger batches = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();
        long t0 = System.currentTimeMillis();

        System.out.printf("Connecting to %s:%d sessionId=%s mode=%s%n",
                host, port, sessionId, continuous ? "continuous" : "bounded");

        try (GrpcServerConnection connection = new GrpcServerConnection(new WorkspaceAddress(host, port))) {
            RemoteEventStreamingClient client = new RemoteEventStreamingClient(connection);

            EventStreamingSubscription sub = client.subscribe(
                    sessionId,
                    eventTypes,
                    startTime,
                    endTime,
                    continuous,
                    batch -> {
                        int n = batch.getEventsCount();
                        int total = events.addAndGet(n);
                        int b = batches.incrementAndGet();
                        long elapsed = System.currentTimeMillis() - t0;
                        System.out.printf("[%7d ms] batch #%d events=%d total=%d%n", elapsed, b, n, total);
                        for (StreamingEvent e : batch.getEventsList()) {
                            System.out.printf("    %s @ %s", e.getEventType(), Instant.ofEpochMilli(e.getTimestamp()).toString());
                        }
                    },
                    () -> {
                        System.out.printf("[%7d ms] stream completed: batches=%d events=%d%n",
                                System.currentTimeMillis() - t0, batches.get(), events.get());
                        done.countDown();
                    },
                    err -> {
                        System.err.printf("[%7d ms] stream error: %s%n",
                                System.currentTimeMillis() - t0, err);
                        done.countDown();
                    });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Interrupt received, cancelling subscription...");
                sub.cancel();
                done.countDown();
            }));

            done.await(10, TimeUnit.MINUTES);
            System.out.printf("[%7d ms] exiting: batches=%d events=%d%n",
                    System.currentTimeMillis() - t0, batches.get(), events.get());
        }
    }

    private EventStreamingProbe() {
    }
}
