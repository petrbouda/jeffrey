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

package pbouda.jeffrey.profile.ai.model;

import java.util.List;

/**
 * Response object from the OQL chat endpoint.
 *
 * @param content            the AI's response text
 * @param oql                extracted OQL query (null if none generated)
 * @param suggestedFollowups suggested follow-up queries the user might want
 */
public record OqlChatResponse(
        String content,
        String oql,
        List<String> suggestedFollowups
) {
    /**
     * Create a response with just content and no OQL.
     *
     * @param content the response text
     * @return a new response with no OQL
     */
    public static OqlChatResponse textOnly(String content) {
        return new OqlChatResponse(content, null, List.of());
    }

    /**
     * Create a response with OQL and auto-generated suggestions.
     *
     * @param content the response text
     * @param oql     the extracted OQL query
     * @return a new response with OQL
     */
    public static OqlChatResponse withOql(String content, String oql) {
        return new OqlChatResponse(content, oql, List.of());
    }

    /**
     * Check if this response contains an OQL query.
     *
     * @return true if an OQL query was generated
     */
    public boolean hasOql() {
        return oql != null && !oql.isBlank();
    }
}
