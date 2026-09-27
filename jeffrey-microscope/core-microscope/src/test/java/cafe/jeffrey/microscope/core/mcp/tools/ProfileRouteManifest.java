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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * The frontend's router snapshot ({@code profile-routes.json}): every profile sub-path the Microscope
 * UI serves. A {@code uiLink} to a page that is not in it would be a link the router's catch-all turns
 * into the recordings list.
 */
final class ProfileRouteManifest {

    private static final Path PROFILE_ROUTES = Path.of("../pages-microscope/src/router/profile-routes.json");

    private ProfileRouteManifest() {
    }

    static Set<String> routes() {
        try {
            Set<String> routes = new HashSet<>();
            for (JsonNode route : Json.readTree(Files.readString(PROFILE_ROUTES))) {
                routes.add(route.asString());
            }
            return routes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
