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

package pbouda.jeffrey.local.core.manager;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.resources.response.UpdateCheckResult;
import pbouda.jeffrey.shared.common.SemanticVersion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class GitHubReleaseChecker {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubReleaseChecker.class);

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/petrbouda/jeffrey/releases/latest";

    private static final String GITHUB_DOWNLOAD_ASSET = "jeffrey.jar";

    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final HttpRequest GITHUB_RELEASE_LATEST = HttpRequest.newBuilder()
            .uri(URI.create(GITHUB_API_URL))
            .header("Accept", "application/vnd.github+json")
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;
    private final HttpClient httpClient;

    private volatile UpdateCheckResult cachedResult;
    private volatile Instant lastChecked;

    public GitHubReleaseChecker(ObjectMapper objectMapper, Clock clock, boolean enabled) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public Optional<UpdateCheckResult> check(String currentVersion) {
        if (!enabled) {
            return Optional.empty();
        }

        if (currentVersion == null || currentVersion.isBlank() || "Unknown".equalsIgnoreCase(currentVersion)) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (cachedResult != null && lastChecked != null && lastChecked.plus(CACHE_TTL).isAfter(now)) {
            return Optional.of(cachedResult);
        }

        try {
            return fetchAndCompare(currentVersion, now);
        } catch (Exception e) {
            LOG.debug("Version update check failed: reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<UpdateCheckResult> fetchAndCompare(String currentVersion, Instant now) throws Exception {
        HttpResponse<String> response = httpClient.send(GITHUB_RELEASE_LATEST, BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOG.debug("GitHub API returned non-200 status: status={}", response.statusCode());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());
        String tagName = root.path("tag_name").asString("");
        String htmlUrl = root.path("html_url").asString("");
        String downloadUrl = findJarDownloadUrl(root);

        Optional<SemanticVersion> currentParsed = SemanticVersion.parse(currentVersion);
        Optional<SemanticVersion> latestParsed = SemanticVersion.parse(tagName);

        if (currentParsed.isEmpty() || latestParsed.isEmpty()) {
            return Optional.empty();
        }

        SemanticVersion current = currentParsed.get();
        SemanticVersion latest = latestParsed.get();

        boolean updateAvailable = latest.compareTo(current) > 0;
        boolean majorUpdate = updateAvailable && latest.isMajorUpgradeFrom(current);

        UpdateCheckResult result = new UpdateCheckResult(
                current.toString(),
                latest.toString(),
                updateAvailable,
                majorUpdate,
                htmlUrl,
                downloadUrl);

        if (updateAvailable) {
            cachedResult = result;
            lastChecked = now;
        }

        return updateAvailable ? Optional.of(result) : Optional.empty();
    }

    private static String findJarDownloadUrl(JsonNode root) {
        JsonNode assets = root.path("assets");
        if (assets.isArray()) {
            for (JsonNode asset : assets) {
                String name = asset.path("name").asString("");
                if (GITHUB_DOWNLOAD_ASSET.equals(name)) {
                    return asset.path("browser_download_url").asString(null);
                }
            }
        }
        return null;
    }
}
