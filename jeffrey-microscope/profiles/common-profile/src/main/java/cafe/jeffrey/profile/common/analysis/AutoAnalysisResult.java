/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.common.analysis;

/**
 * @param topic the JMC rule topic the rule belongs to — {@code garbage_collection}, {@code exceptions},
 *              {@code lock_instances} — which is what groups findings by subsystem and routes a reader
 *              to the dashboard that carries the figures. Null for a result cached before the field
 *              existed
 */
public record AutoAnalysisResult(
        String rule,
        Severity severity,
        String explanation,
        String summary,
        String solution,
        String score,
        String topic) implements AnalysisResult {
}
