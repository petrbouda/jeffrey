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

package cafe.jeffrey.microscope.core.recovery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Minimal web server (JDK built-in {@link HttpServer}, no Spring, no Tomcat) that serves the
 * recovery page on Jeffrey's regular HTTP port while the application cannot start. Once the
 * user completes one of the recovery actions the server is stopped, the port is released and
 * the same JVM continues into the normal Spring Boot startup.
 */
public final class RecoveryWebServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RecoveryWebServer.class);

    private static final String ROOT_PATH = "/";
    private static final String API_PATH_PREFIX = "/api/";
    private static final String STATUS_PATH = "/api/recovery/status";
    private static final String START_FRESH_PATH = "/api/recovery/start-fresh";
    private static final String SWITCH_HOME_PATH = "/api/recovery/switch-home";

    private static final String PAGE_RESOURCE = "/recovery/recovery.html";

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_HTML = "text/html; charset=utf-8";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private static final int SERVER_STOP_GRACE_SECONDS = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    record StatusResponse(String homeDir) {
    }

    record SwitchHomeRequest(String path) {
    }

    record ErrorResponse(String error) {
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final Path currentHome;
    private final Supplier<Path> startFreshAction;
    private final Function<String, Path> switchHomeAction;
    private final CompletableFuture<Path> completedHome = new CompletableFuture<>();

    public RecoveryWebServer(
            int port,
            Path currentHome,
            Supplier<Path> startFreshAction,
            Function<String, Path> switchHomeAction) throws IOException {

        this.currentHome = currentHome;
        this.startFreshAction = startFreshAction;
        this.switchHomeAction = switchHomeAction;

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        server.createContext(ROOT_PATH, this::handlePage);
        server.createContext(STATUS_PATH, this::handleStatus);
        server.createContext(START_FRESH_PATH, this::handleStartFresh);
        server.createContext(SWITCH_HOME_PATH, this::handleSwitchHome);
        server.setExecutor(executor);
        server.start();
        LOG.info("Recovery web server started: port={} home_dir={}", port(), currentHome);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Blocks until one of the recovery actions completed successfully and returns the home
     * directory Jeffrey should continue booting with.
     */
    public Path awaitAction() {
        return completedHome.join();
    }

    @Override
    public void close() {
        server.stop(SERVER_STOP_GRACE_SECONDS);
        executor.close();
        LOG.info("Recovery web server stopped: home_dir={}", currentHome);
    }

    private void handlePage(HttpExchange exchange) throws IOException {
        if (exchange.getRequestURI().getPath().startsWith(API_PATH_PREFIX)) {
            respondEmpty(exchange, 404);
            return;
        }
        if (!METHOD_GET.equals(exchange.getRequestMethod())) {
            respondEmpty(exchange, 405);
            return;
        }
        try (InputStream page = RecoveryWebServer.class.getResourceAsStream(PAGE_RESOURCE)) {
            byte[] body = page.readAllBytes();
            exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, CONTENT_TYPE_HTML);
            respond(exchange, 200, body);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!METHOD_GET.equals(exchange.getRequestMethod())) {
            respondEmpty(exchange, 405);
            return;
        }
        respondJson(exchange, 200, new StatusResponse(currentHome.toString()));
    }

    private void handleStartFresh(HttpExchange exchange) throws IOException {
        handleAction(exchange, () -> startFreshAction.get());
    }

    private void handleSwitchHome(HttpExchange exchange) throws IOException {
        handleAction(exchange, () -> {
            SwitchHomeRequest request;
            try {
                request = JSON.readValue(exchange.getRequestBody(), SwitchHomeRequest.class);
            } catch (JacksonException e) {
                throw new IllegalArgumentException("Invalid request body.");
            }
            return switchHomeAction.apply(request.path());
        });
    }

    private void handleAction(HttpExchange exchange, Supplier<Path> action) throws IOException {
        if (!METHOD_POST.equals(exchange.getRequestMethod())) {
            respondEmpty(exchange, 405);
            return;
        }

        Path newHome;
        try {
            newHome = action.get();
        } catch (IllegalArgumentException e) {
            respondJson(exchange, 400, new ErrorResponse(e.getMessage()));
            return;
        } catch (RuntimeException e) {
            LOG.error("Recovery action failed: message={}", e.getMessage(), e);
            respondJson(exchange, 500, new ErrorResponse(e.getMessage()));
            return;
        }

        respondJson(exchange, 200, new StatusResponse(newHome.toString()));
        completedHome.complete(newHome);
    }

    private static void respondJson(HttpExchange exchange, int status, Object body) throws IOException {
        exchange.getResponseHeaders().set(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON);
        respond(exchange, status, JSON.writeValueAsBytes(body));
    }

    private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
