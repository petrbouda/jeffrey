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

package cafe.jeffrey.microscope.core.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcModernServerTransport;
import org.springframework.context.SmartLifecycle;

import java.lang.reflect.Field;

/**
 * Makes Spring AI 2.1.0's modern MCP server lifecycle restart-safe.
 * <p>
 * The auto-configured {@code webMvcModernServerTransportLifecycle} (a {@link SmartLifecycle}) stops by
 * calling {@link WebMvcModernServerTransport#closeGracefully()}, which sets a one-way, private
 * {@code closing} flag; its {@code start()} never clears it. So once Spring stops and then restarts the
 * lifecycle, the transport answers every request {@code 503 "Server is shutting down"} for good. Spring
 * does exactly that stop/restart whenever it reuses a cached context - which the test context cache does
 * across booted integration classes - and an in-process context restart would do the same in production.
 * <p>
 * This wrapper delegates every method but clears the flag on {@code start()} before delegating, so a
 * restarted server serves again. {@code stop()} still delegates, so the transport's own graceful
 * shutdown and resource cleanup run as before. If the SDK's field is ever renamed the reset is skipped
 * with a warning rather than failing startup; a first start (flag already false) is unaffected either way.
 */
final class RestartSafeMcpServerLifecycle implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(RestartSafeMcpServerLifecycle.class);
    private static final String CLOSING_FIELD = "closing";

    private final SmartLifecycle delegate;
    private final WebMvcModernServerTransport transport;

    RestartSafeMcpServerLifecycle(SmartLifecycle delegate, WebMvcModernServerTransport transport) {
        this.delegate = delegate;
        this.transport = transport;
    }

    @Override
    public void start() {
        reopen();
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public void stop(Runnable callback) {
        delegate.stop(callback);
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public int getPhase() {
        return delegate.getPhase();
    }

    @Override
    public boolean isAutoStartup() {
        return delegate.isAutoStartup();
    }

    private void reopen() {
        try {
            Field closing = WebMvcModernServerTransport.class.getDeclaredField(CLOSING_FIELD);
            closing.setAccessible(true);
            closing.setBoolean(transport, false);
        } catch (ReflectiveOperationException e) {
            LOG.warn("Could not reset the modern MCP transport closing flag on restart: reason={}", e.getMessage());
        }
    }
}
