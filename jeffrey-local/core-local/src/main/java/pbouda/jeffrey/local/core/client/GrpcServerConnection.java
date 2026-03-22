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

package pbouda.jeffrey.local.core.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * Manages a gRPC {@link ManagedChannel} to a remote Jeffrey server.
 * Replaces {@link RemoteHttpInvoker} for gRPC-based communication.
 */
public class GrpcServerConnection implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerConnection.class);

    private final String host;
    private final int port;
    private final ManagedChannel channel;

    public GrpcServerConnection(String host, int port) {
        this.host = host;
        this.port = port;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        LOG.info("Created gRPC connection: host={} port={}", host, port);
    }

    /**
     * Returns the underlying {@link ManagedChannel} for creating gRPC stubs.
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    /**
     * Returns the remote host.
     */
    public String host() {
        return host;
    }

    /**
     * Returns the remote port.
     */
    public int port() {
        return port;
    }

    @Override
    public void close() {
        LOG.info("Shutting down gRPC connection: host={} port={}", host, port);
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("gRPC channel shutdown interrupted, forcing shutdown: host={} port={}", host, port);
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
