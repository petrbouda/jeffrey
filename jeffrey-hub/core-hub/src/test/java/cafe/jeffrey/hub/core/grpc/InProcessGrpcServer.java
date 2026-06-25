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
