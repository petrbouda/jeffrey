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
