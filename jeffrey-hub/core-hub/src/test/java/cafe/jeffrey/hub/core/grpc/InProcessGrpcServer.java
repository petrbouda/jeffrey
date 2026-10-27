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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Test helper that starts a single gRPC {@link BindableService} on an in-process server with a
 * direct executor and exposes a {@link ManagedChannel} to it. Replaces the per-test-class
 * {@code InProcessServerBuilder}/{@code InProcessChannelBuilder} boilerplate; create one per test
 * and close it (directly or via {@code @AfterEach}) to release the server and channel.
 *
 * <p>Error mapping is handled inside the service methods by {@link GrpcUnary}/{@link GrpcExceptions},
 * so the in-process tests observe the real exception-to-status mapping without extra wiring.
 */
public final class InProcessGrpcServer implements AutoCloseable {

    private final Server server;
    private final ManagedChannel channel;

    private InProcessGrpcServer(Server server, ManagedChannel channel) {
        this.server = server;
        this.channel = channel;
    }

    public static InProcessGrpcServer start(BindableService service) {
        String name = InProcessServerBuilder.generateName();
        try {
            Server server = InProcessServerBuilder.forName(name)
                    .directExecutor()
                    .addService(service)
                    .build()
                    .start();
            ManagedChannel channel = InProcessChannelBuilder.forName(name)
                    .directExecutor()
                    .build();
            return new InProcessGrpcServer(server, channel);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start in-process gRPC server", e);
        }
    }

    public ManagedChannel channel() {
        return channel;
    }

    @Override
    public void close() {
        channel.shutdownNow();
        server.shutdownNow();
    }
}
