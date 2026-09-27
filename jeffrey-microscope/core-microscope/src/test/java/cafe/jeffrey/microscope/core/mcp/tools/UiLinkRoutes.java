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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether a {@code uiLink} lands on a page the Microscope UI serves. A profile page is checked against
 * the router snapshot ({@code profile-routes.json}, via {@link ProfileRouteManifest}); the profile's
 * own landing page is {@code /profiles/{id}}; a page outside a profile, such as the recordings list,
 * has to be in the pinned top-level manifest ({@code global-routes.json}), which the frontend's
 * {@code globalRouteManifest.spec.ts} holds to the router.
 * <p>
 * A link's query is held to the frontend's link contract ({@code link-params.json}): every parameter
 * has to be one the page it lands on declares it reads. A parameter the page ignores opens it
 * unfiltered while the answer says otherwise, so an undeclared one fails, and a page with no entry
 * reads none. The frontend's {@code linkParams.spec.ts} pins the file and derives its own parameter
 * names from it.
 */
public final class UiLinkRoutes {

    private static final Pattern PROFILE_PATH = Pattern.compile("^/profiles/([^/]+)(?:/(.+))?$");
    private static final Pattern GLOBAL_PATH = Pattern.compile("^/([a-z][a-z0-9-]*)$");
    private static final Path GLOBAL_ROUTES = Path.of("../pages-microscope/src/router/global-routes.json");
    private static final Path LINK_PARAMS = Path.of("../pages-microscope/src/router/link-params.json");
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String VALUE_SEPARATOR = "=";

    private UiLinkRoutes() {
    }

    /**
     * @throws AssertionError when the link is missing, not absolute, or lands on no page the router serves
     */
    public static void assertResolves(String uiLink) {
        assertTrue(uiLink != null && !uiLink.isBlank(), "uiLink is missing");
        URI uri = URI.create(uiLink);
        assertTrue(uri.isAbsolute(), "uiLink is not absolute: " + uiLink);
        assertTrue(resolves(uri.getPath()), "uiLink lands on no page the Microscope UI serves: " + uiLink);
        Set<String> undeclared = new TreeSet<>(queryParameters(uri));
        undeclared.removeAll(declaredParameters(uri.getPath()));
        assertTrue(undeclared.isEmpty(), "uiLink carries query parameters its page does not read "
                + "(link-params.json): " + undeclared + " in " + uiLink);
    }

    /**
     * The query parameters each profile page reads, by its sub-path, as the frontend's link contract
     * declares them. A page not in it reads none.
     */
    public static Map<String, Set<String>> linkParameters() {
        try {
            Map<String, Set<String>> declared = new HashMap<>();
            JsonNode pages = Json.readTree(Files.readString(LINK_PARAMS));
            for (Map.Entry<String, JsonNode> page : pages.properties()) {
                Set<String> names = new HashSet<>();
                for (JsonNode name : page.getValue()) {
                    names.add(name.asString());
                }
                declared.put(page.getKey(), Set.copyOf(names));
            }
            return Map.copyOf(declared);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> declaredParameters(String path) {
        Matcher profile = PROFILE_PATH.matcher(path);
        if (!profile.matches() || profile.group(2) == null) {
            return Set.of();
        }
        return linkParameters().getOrDefault(profile.group(2), Set.of());
    }

    private static Set<String> queryParameters(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(query.split(PARAMETER_SEPARATOR))
                .map(parameter -> parameter.split(VALUE_SEPARATOR, 2)[0])
                .map(name -> URLDecoder.decode(name, StandardCharsets.UTF_8))
                .collect(Collectors.toSet());
    }

    private static boolean resolves(String path) {
        Matcher profile = PROFILE_PATH.matcher(path);
        if (profile.matches()) {
            return profile.group(2) == null || ProfileRouteManifest.routes().contains(profile.group(2));
        }
        Matcher global = GLOBAL_PATH.matcher(path);
        return global.matches() && globalRoutes().contains(global.group(1));
    }

    /** Every top-level page of the application shell, as the frontend's pinned manifest lists them. */
    public static Set<String> globalRoutes() {
        try {
            Set<String> routes = new HashSet<>();
            for (JsonNode route : Json.readTree(Files.readString(GLOBAL_ROUTES))) {
                routes.add(route.asString());
            }
            return routes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every page under a profile, as the frontend's pinned manifest lists them. */
    public static Set<String> profileRoutes() {
        return ProfileRouteManifest.routes();
    }
}
