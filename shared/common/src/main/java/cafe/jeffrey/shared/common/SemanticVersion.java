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

package cafe.jeffrey.shared.common;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {

    // Matches build number qualifier like "-b216", "-b1"
    private static final Pattern BUILD_QUALIFIER = Pattern.compile("-b(\\d+)$");

    /**
     * Parses a version string into a {@link SemanticVersion}.
     * <p>
     * Handles formats:
     * <ul>
     *   <li>{@code "v1.2.3"}, {@code "1.2.3"} — standard semver</li>
     *   <li>{@code "v0.6-b216"}, {@code "0.6-b216"} — build number used as patch</li>
     *   <li>{@code "1.0-SNAPSHOT"} — qualifier stripped, treated as 1.0.0</li>
     * </ul>
     * Returns empty for "Unknown", blank, or unparseable strings.
     */
    public static Optional<SemanticVersion> parse(String version) {
        if (version == null || version.isBlank() || "Unknown".equalsIgnoreCase(version)) {
            return Optional.empty();
        }

        String cleaned = version.strip();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }

        // Check for build number qualifier (e.g., "0.6-b216" → major=0, minor=6, patch=216)
        int buildNumber = -1;
        Matcher buildMatcher = BUILD_QUALIFIER.matcher(cleaned);
        if (buildMatcher.find()) {
            buildNumber = Integer.parseInt(buildMatcher.group(1));
            cleaned = cleaned.substring(0, buildMatcher.start());
        } else {
            // Strip non-build qualifiers (e.g., "-SNAPSHOT", "-RC1")
            int dashIndex = cleaned.indexOf('-');
            if (dashIndex > 0) {
                cleaned = cleaned.substring(0, dashIndex);
            }
        }

        String[] parts = cleaned.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = buildNumber >= 0 ? buildNumber : (parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
            return Optional.of(new SemanticVersion(major, minor, patch));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean isMajorUpgradeFrom(SemanticVersion older) {
        return this.major > older.major;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
