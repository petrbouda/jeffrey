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

package cafe.jeffrey.intellij;

import cafe.jeffrey.intellij.dto.NavigateRequest;
import cafe.jeffrey.intellij.resolver.JavaResolver;
import cafe.jeffrey.intellij.settings.JeffreySettings;
import cafe.jeffrey.intellij.util.Json;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.RestService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP entry point for the Jeffrey Microscope integration, served over IntelliJ's built-in Netty
 * server (extension point {@code com.intellij.httpRequestHandler}). Reachable at
 * {@code /api/jeffrey/*} ({@link #PREFIX} is {@code /api}; service name {@code jeffrey}).
 *
 * <p>Endpoints: {@code GET ping}, {@code GET instance}, {@code POST navigate}, {@code GET has-class},
 * {@code GET source}. There is no app-level token: the built-in server binds to localhost, so
 * Microscope discovers and calls instances simply by scanning the port range. Requests carrying a
 * browser {@code Origin} still fall under IntelliJ's default cross-origin protection (see
 * {@link #isOriginAllowed}). When the integration is disabled in settings every endpoint returns
 * {@code 404}, so a disabled IDE is invisible to Microscope's scan.
 */
public final class JeffreyMicroscopeService extends RestService {

    /** Bumped on breaking wire-protocol changes; echoed by responses. */
    static final int PROTOCOL_VERSION = 1;

    private static final String SERVICE_NAME = "jeffrey";

    private static final String PATH_PING = "ping";
    private static final String PATH_INSTANCE = "instance";
    private static final String PATH_NAVIGATE = "navigate";
    private static final String PATH_HAS_CLASS = "has-class";
    private static final String PATH_SOURCE = "source";

    private static final String PARAM_FQCN = "fqcn";
    private static final String PARAM_PROJECT_ID = "projectId";
    private static final String PARAM_CLASS_NAME = "className";

    @NotNull
    @Override
    protected String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    protected boolean isMethodSupported(@NotNull HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.POST;
    }

    @NotNull
    @Override
    protected OriginCheckResult isOriginAllowed(@NotNull HttpRequest request) {
        // Microscope's backend calls server-side and sends no Origin — allow those. Browser-issued
        // requests (which carry an Origin) defer to the platform's default cross-origin protection.
        if (request.headers().get(HttpHeaderNames.ORIGIN) == null) {
            return OriginCheckResult.ALLOW;
        }
        return super.isOriginAllowed(request);
    }

    @Nullable
    @Override
    public String execute(@NotNull QueryStringDecoder urlDecoder,
                          @NotNull FullHttpRequest request,
                          @NotNull ChannelHandlerContext context) throws IOException {
        if (!JeffreySettings.getInstance().isEnabled()) {
            sendStatus(HttpResponseStatus.NOT_FOUND, HttpUtil.isKeepAlive(request), context.channel());
            return null;
        }

        switch (subPath(urlDecoder.path())) {
            case PATH_PING -> sendJson(Json.ping(PROTOCOL_VERSION), request, context);
            case PATH_INSTANCE -> sendJson(Json.instance(ProjectRegistry.getInstance().currentInstance()), request, context);
            case PATH_NAVIGATE -> handleNavigate(request, context);
            case PATH_HAS_CLASS -> handleHasClass(urlDecoder, request, context);
            case PATH_SOURCE -> handleSource(urlDecoder, request, context);
            default -> {
                return "Unknown Jeffrey endpoint";
            }
        }
        return null;
    }

    private void handleNavigate(@NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
        NavigateRequest req = Json.parseNavigate(request.content().toString(StandardCharsets.UTF_8));
        sendJson(Json.navigate(Navigator.navigate(req)), request, context);
    }

    private void handleHasClass(@NotNull QueryStringDecoder urlDecoder,
                                @NotNull FullHttpRequest request,
                                @NotNull ChannelHandlerContext context) throws IOException {
        String fqcn = getStringParameter(PARAM_FQCN, urlDecoder);
        String projectId = getStringParameter(PARAM_PROJECT_ID, urlDecoder);
        Project project = ProjectRegistry.findProject(projectId);
        boolean found = project != null && fqcn != null && JavaResolver.exists(project, fqcn);
        sendJson(Json.hasClass(found, projectId), request, context);
    }

    private void handleSource(@NotNull QueryStringDecoder urlDecoder,
                              @NotNull FullHttpRequest request,
                              @NotNull ChannelHandlerContext context) throws IOException {
        String projectId = getStringParameter(PARAM_PROJECT_ID, urlDecoder);
        String className = getStringParameter(PARAM_CLASS_NAME, urlDecoder);
        Project project = ProjectRegistry.findProject(projectId);
        sendJson(Json.source(Navigator.fetchSource(project, className)), request, context);
    }

    private static void sendJson(byte[] body,
                                 @NotNull FullHttpRequest request,
                                 @NotNull ChannelHandlerContext context) throws IOException {
        BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
        out.write(body);
        send(out, request, context);
    }

    /** Strips the {@code /api/jeffrey/} prefix, returning the remaining path (e.g. {@code "ping"}). */
    private static String subPath(@NotNull String fullPath) {
        String prefix = PREFIX + "/" + SERVICE_NAME + "/";
        int idx = fullPath.indexOf(prefix);
        if (idx < 0) {
            return "";
        }
        return fullPath.substring(idx + prefix.length());
    }
}
