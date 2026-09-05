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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;

/**
 * The catalogue of what has been analysed — the entry point of an MCP session.
 * <p>
 * The only family that is not profile-scoped: this is what a client calls before it has a profile id
 * at all. Everything you can ask <em>about</em> one profile lives in {@link ProfileMcpTools}, which
 * shares this prefix so the two read as one family to the model.
 */
public class ProfilesMcpTools {

    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 1000;

    private static final String QUICK_ANALYSIS_PROJECT = "(quick analysis)";
    private static final String NO_PROFILES =
            "No profiles have been analysed yet. Upload a JFR recording or heap dump in Jeffrey and "
                    + "run Analyze first.";

    private final MicroscopeCoreRepositories coreRepositories;

    public ProfilesMcpTools(MicroscopeCoreRepositories coreRepositories) {
        this.coreRepositories = coreRepositories;
    }

    @Tool(description = "List every profile analysed in this Jeffrey installation. Start here: every "
            + "other tool takes one of the profile ids this returns. A profile is one analysed "
            + "recording (JFR) or heap dump.")
    public String list(
            @ToolParam(required = false, description = "Optional case-insensitive substring matched against the profile name")
            String search,
            @ToolParam(required = false, description = "Maximum number of profiles to return (default 100)")
            Integer limit) {

        List<ProfileInfo> profiles = coreRepositories.findAllProfiles().stream()
                .filter(profile -> matches(profile, search))
                .limit(boundedLimit(limit))
                .toList();

        if (profiles.isEmpty()) {
            return search == null || search.isBlank()
                    ? NO_PROFILES
                    : "No profile matches: " + search;
        }

        StringBuilder out = new StringBuilder(1024);
        out.append("| profile_id | name | project | event source | recorded | duration | modified |\n");
        out.append("|---|---|---|---|---|---|---|\n");
        for (ProfileInfo profile : profiles) {
            out.append("| ").append(profile.id())
                    .append(" | ").append(sanitize(profile.name()))
                    .append(" | ").append(projectOf(profile))
                    .append(" | ").append(profile.eventSource())
                    .append(" | ").append(profile.profilingStartedAt())
                    .append(" | ").append(profile.duration())
                    .append(" | ").append(profile.modified() ? "yes" : "no")
                    .append(" |\n");
        }
        out.append("\nA `modified` profile has had frames renamed or collapsed, so its frame names may "
                + "differ from the source code.\n");
        return McpToolOutput.capped(out.toString());
    }

    private static long boundedLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private static boolean matches(ProfileInfo profile, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String name = profile.name() == null ? "" : profile.name();
        return name.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * A Quick Analysis profile belongs to no project — it was opened straight from a local file.
     */
    private static String projectOf(ProfileInfo profile) {
        return profile.projectId() == null ? QUICK_ANALYSIS_PROJECT : profile.projectId();
    }

    /**
     * Keeps a name on one table row: a pipe in a profile name would otherwise split the cell.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }
}
