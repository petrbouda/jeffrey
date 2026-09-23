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

package cafe.jeffrey.microscope.grpc.client;

import cafe.jeffrey.jfr.events.grpc.interceptor.JfrGrpcClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.model.hub.HubAddress;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * Manages a gRPC {@link ManagedChannel} to a Jeffrey Hub.
 */
public class GrpcHubConnection implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcHubConnection.class);

    private final HubAddress address;
    private final ManagedChannel channel;

    public GrpcHubConnection(HubAddress address) {
        this.address = address;

        // Every call over this channel is recorded as a span under whatever the caller is doing,
        // so a hub round-trip shows up inside the request or job that triggered it.
        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(address.hostname(), address.port())
                .intercept(new JfrGrpcClientInterceptor());
        if (address.plaintext()) {
            builder.usePlaintext();
        } else {
            try {
                SslContext sslContext = GrpcSslContexts.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                builder.sslContext(sslContext);
            } catch (SSLException e) {
                throw new RuntimeException("Failed to create TLS context for gRPC connection: " + address, e);
            }
        }
        this.channel = builder.build();
        LOG.info("Created gRPC connection: target={}:{} plaintext={}",
                address.hostname(), address.port(), address.plaintext());
    }

    /**
     * Wraps an already-built channel instead of dialling an address. Exists so a test can point
     * the clients at an in-process server; production code should use
     * {@link #GrpcHubConnection(HubAddress)}.
     */
    protected GrpcHubConnection(ManagedChannel channel) {
        this.address = null;
        this.channel = channel;
    }

    /**
     * Returns the underlying {@link ManagedChannel} for creating gRPC stubs.
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    public HubAddress address() {
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
