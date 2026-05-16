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
package cafe.jeffrey.profile.heapdump.model;

/**
 * One heuristic finding from the Leak Hints rule engine. Designed to be
 * rendered as a card with a severity, a one-line title, and a short
 * explanatory body — keeps the user oriented when triaging a fresh heap dump.
 *
 * @param severity see {@link Severity}
 * @param ruleId   stable identifier of the rule that produced this finding
 *                 (useful when the UI wants to deep-link or suppress
 *                 specific findings)
 * @param title    short title (~ 60 chars)
 * @param details  longer prose; can include simple HTML-safe text
 */
public record LeakHintFinding(
        Severity severity,
        String ruleId,
        String title,
        String details
) {

    public enum Severity { HIGH, MEDIUM, LOW }
}
