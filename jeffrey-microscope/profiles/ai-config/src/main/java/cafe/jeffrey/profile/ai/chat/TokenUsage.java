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

package cafe.jeffrey.profile.ai.chat;

/**
 * What one model call consumed. Providers report this inconsistently — an API returns token counts and
 * leaves pricing to the caller, while the Claude Code CLI reports a settled cost and no per-call rate —
 * so {@code costUsd} is nullable rather than derived from a price table Jeffrey would have to keep
 * current.
 *
 * @param inputTokens  tokens sent, including tool results; zero when the provider did not report it
 * @param outputTokens tokens generated; zero when the provider did not report it
 * @param costUsd      the provider's own cost figure, or null when it reports none
 */
public record TokenUsage(long inputTokens, long outputTokens, Double costUsd) {

    private static final TokenUsage UNKNOWN = new TokenUsage(0, 0, null);

    /**
     * Used when a provider surfaces no usage at all, so callers never have to null-check.
     */
    public static TokenUsage unknown() {
        return UNKNOWN;
    }

    public boolean isKnown() {
        return inputTokens > 0 || outputTokens > 0 || costUsd != null;
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }
}
