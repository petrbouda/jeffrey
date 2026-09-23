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

package cafe.jeffrey.profile.model;

/**
 * The "Use weight" flamegraph-card setting. {@code applicable} whether the toggle is offered,
 * {@code defaultOn} its initial state, {@code label} the toggle text (e.g. "CPU Time", "Total
 * Allocation"; null when not applicable), and {@code kind} how the weight value is formatted. The kind
 * is always set — the frontend also uses it to format the sample-interval detail row even when the
 * weight toggle itself is hidden.
 */
public record WeightOption(boolean applicable, boolean defaultOn, String label, WeightKind kind) {

    public WeightOption {
        if (kind == null) {
            throw new IllegalArgumentException("WeightOption.kind must not be null");
        }
    }
}
