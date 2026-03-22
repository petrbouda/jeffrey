/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts OQL queries from AI response text.
 * Handles various formats including code blocks and plain text.
 */
public class OqlExtractor {

    // Pattern to find OQL in code blocks (```sql or ```oql or just ```)
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:sql|oql)?\\s*(select.+?)```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Pattern to find standalone SELECT statements
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "^\\s*(select\\s+.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    /**
     * Extract OQL query from AI response text.
     *
     * @param response the AI response text
     * @return the extracted OQL query, or null if none found
     */
    public String extract(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        // First try to find OQL in code blocks
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(response);
        if (codeBlockMatcher.find()) {
            return normalizeOql(codeBlockMatcher.group(1));
        }

        // Try to find standalone SELECT statement
        Matcher selectMatcher = SELECT_PATTERN.matcher(response);
        if (selectMatcher.find()) {
            return normalizeOql(selectMatcher.group(1));
        }

        return null;
    }

    /**
     * Remove the extracted OQL code block from the response text
     * to get a cleaner content message.
     *
     * @param response the original response
     * @param oql      the extracted OQL
     * @return cleaned response text
     */
    public String cleanResponse(String response, String oql) {
        if (response == null || oql == null) {
            return response;
        }

        // Remove code blocks containing the OQL
        String cleaned = CODE_BLOCK_PATTERN.matcher(response).replaceAll("");

        // Clean up extra whitespace
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n").trim();

        // If response is empty after cleaning, provide default
        if (cleaned.isBlank()) {
            return "Here's a query for that:";
        }

        return cleaned;
    }

    private String normalizeOql(String oql) {
        if (oql == null) {
            return null;
        }
        // Remove leading/trailing whitespace and normalize internal whitespace
        return oql.trim().replaceAll("\\s+", " ");
    }
}
