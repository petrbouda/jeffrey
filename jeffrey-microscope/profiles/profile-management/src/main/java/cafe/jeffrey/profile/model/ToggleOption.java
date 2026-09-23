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
 * A boolean flamegraph-card setting: whether the toggle is offered at all ({@code applicable}) and its
 * initial checked state ({@code defaultOn}). Used for thread-mode, exclude-non-Java, exclude-idle and
 * only-unsafe-allocation toggles.
 */
public record ToggleOption(boolean applicable, boolean defaultOn) {

    public static final ToggleOption OFF = new ToggleOption(false, false);
    public static final ToggleOption ON = new ToggleOption(true, true);
}
