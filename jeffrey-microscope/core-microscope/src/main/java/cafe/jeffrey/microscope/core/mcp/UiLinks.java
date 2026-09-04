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

package cafe.jeffrey.microscope.core.mcp;

import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Links into the Jeffrey web UI, for the person reading what a tool just answered.
 * <p>
 * The host is taken from the request being served rather than from configuration: the client already
 * reached this installation, so the address it used is by definition one that works for it. That is
 * also the constraint — {@link ServletUriComponentsBuilder#fromCurrentContextPath()} reads the
 * request bound to the current thread, so these methods only work while a request is being handled.
 * MCP tools are invoked synchronously inside the controller, which is what makes it safe here.
 * <p>
 * The frontend runs in HTML5 history mode with no context path, so a view is a plain path — there is
 * no {@code #} fragment to reproduce.
 */
public final class UiLinks {

    private static final String PROFILE_PATH = "/profiles/%s";
    private static final String PROFILE_VIEW_PATH = "/profiles/%s/%s";

    /**
     * How the frontend spells a true flag. The views compare against the literal string, and the
     * generating side omits false ones entirely, so a link must do the same rather than send
     * {@code false} and rely on it being parsed.
     */
    public static final String TRUE = "true";

    private UiLinks() {
    }

    /**
     * The profile's landing page.
     */
    public static String profile(String profileId) {
        return builder()
                .replacePath(PROFILE_PATH.formatted(profileId))
                .toUriString();
    }

    /**
     * A view inside a profile, with no query of its own.
     */
    public static String view(String profileId, String subPath) {
        return view(profileId, subPath, Map.of());
    }

    /**
     * A view inside a profile. Entries whose value is {@code null} or blank are left out, so a caller
     * can pass an optional argument straight through without branching; values are URL-encoded, which
     * matters for the paths and SQL fragments that travel in these links.
     */
    public static String view(String profileId, String subPath, Map<String, String> query) {
        UriComponentsBuilder builder = builder()
                .replacePath(PROFILE_VIEW_PATH.formatted(profileId, subPath));

        for (Map.Entry<String, String> entry : query.entrySet()) {
            String value = entry.getValue();
            if (value != null && !value.isBlank()) {
                builder.queryParam(entry.getKey(), UriUtils.encode(value, StandardCharsets.UTF_8));
            }
        }
        // Encoded here rather than by the builder, and handed over as already-encoded: the builder
        // reads braces as URI-template placeholders, and Jeffrey's own endpoints are templated
        // ("/api/internal/profiles/{profileId}/gc"), so leaving that to it would put a placeholder in
        // the link and fail outright on an unbalanced brace.
        return builder.build(true).toUriString();
    }

    /**
     * A mutable, insertion-ordered map for building a query a few entries at a time. Ordering is kept
     * so a generated link is stable and diffable across calls.
     */
    public static Map<String, String> query() {
        return new LinkedHashMap<>();
    }

    /**
     * The literal a boolean flag has to become, or {@code null} when the flag is off and the parameter
     * should be dropped.
     */
    public static String flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? TRUE : null;
    }

    private static UriComponentsBuilder builder() {
        return ServletUriComponentsBuilder.fromCurrentContextPath();
    }
}
