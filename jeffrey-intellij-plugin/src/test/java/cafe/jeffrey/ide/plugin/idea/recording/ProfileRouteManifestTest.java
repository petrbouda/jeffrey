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

package cafe.jeffrey.ide.plugin.idea.recording;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Every path this panel links to has to be a route the Microscope frontend actually serves.
 *
 * <p>Nothing else can check that. The frontend is a separate build with its own router, and a tile
 * pointing at a path it does not know does not fail anywhere: the router's catch-all sends the
 * reader to the recordings list, which looks like a working link that went to the wrong place. One
 * heap-dump tile shipped that way, spelling the class-loader route with a hyphen the router does not
 * have.
 *
 * <p>The manifest is generated from the router itself and committed by the frontend, then copied in
 * here by the build. If this test fails after a route was renamed, the fix is on whichever side is
 * wrong — not in the manifest, which is only ever regenerated from the router.
 */
public class ProfileRouteManifestTest {

    private static final String MANIFEST = "/profile-routes.json";

    @Test
    public void everyViewTileLinksToARouteTheFrontendServes() throws IOException {
        Set<String> routes = routes();
        List<String> missing = new ArrayList<>();
        for (ProfileView view : allViews()) {
            if (!routes.contains(view.path())) {
                missing.add(view.label() + " -> " + view.path());
            }
        }

        assertTrue("view tiles pointing at routes the frontend does not serve: " + missing,
                missing.isEmpty());
    }

    /**
     * The landing paths are chosen by kind rather than by tile, so they are reachable from nowhere
     * else in this file and would otherwise be the one pair nothing checks.
     */
    @Test
    public void bothLandingPathsAreRoutesTheFrontendServes() throws IOException {
        Set<String> routes = routes();

        assertTrue("recording landing path", routes.contains("dashboard"));
        assertTrue("heap dump landing path", routes.contains("heap-dump/overview"));
    }

    @Test
    public void theManifestIsThereToBeCheckedAgainst() throws IOException {
        assertFalse("the copied manifest is empty", routes().isEmpty());
    }

    private static List<ProfileView> allViews() {
        List<ProfileView> views = new ArrayList<>(ProfileView.RECORDING);
        views.addAll(ProfileView.HEAP);
        views.addAll(ProfileView.DIFFERENTIAL);
        return views;
    }

    private static Set<String> routes() throws IOException {
        try (InputStream stream = ProfileRouteManifestTest.class.getResourceAsStream(MANIFEST)) {
            assertNotNull("profile-routes.json is missing: the Gradle copy from the frontend did not run",
                    stream);
            JsonArray array = JsonParser
                    .parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            Set<String> routes = new HashSet<>();
            for (JsonElement element : array) {
                routes.add(element.getAsString());
            }
            return routes;
        }
    }
}
