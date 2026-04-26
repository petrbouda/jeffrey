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

package pbouda.jeffrey.local.core.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.persistence.model.WorkspaceAddress;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * Manages a gRPC {@link ManagedChannel} to a remote Jeffrey server.
 */
public class GrpcServerConnection implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcServerConnection.class);

    private final WorkspaceAddress address;
    private final ManagedChannel channel;

    public GrpcServerConnection(WorkspaceAddress address) {
        this.address = address;

        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(address.hostname(), address.port());
        try {
            SslContext sslContext = GrpcSslContexts.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();

            builder.sslContext(sslContext);
        } catch (SSLException e) {
            throw new RuntimeException("Failed to create TLS context for gRPC connection: " + address, e);
        }
        this.channel = builder.build();
        LOG.info("Created gRPC connection: target={}:{}", address.hostname(), address.port());
    }

    /**
     * Returns the underlying {@link ManagedChannel} for creating gRPC stubs.
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    public WorkspaceAddress address() {
        return address;
    }

    @Override
    public void close() {
        LOG.info("Shutting down gRPC connection: address={}", address);
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.warn("gRPC channel shutdown interrupted, forcing shutdown: address={}", address);
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
