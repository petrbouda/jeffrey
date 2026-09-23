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

package cafe.jeffrey.microscope.core.manager.ide;

/**
 * Why an IDE jump failed, so the frontend can react appropriately. {@link #NO_TARGET} and
 * {@link #UNREACHABLE} mean the linked window is missing/offline and re-selecting a target may help —
 * the UI offers a "Select IDE target" action. {@link #NOT_RESOLVED} (the IDE was reached but couldn't
 * resolve the symbol) and {@link #DISABLED} are reported as plain messages; re-picking would not help.
 */
public enum IdeFailureReason {

    /** No failure — the operation succeeded. */
    NONE,
    /** IDE integration is turned off ({@code mode=off}) or unconfigured. */
    DISABLED,
    /** No window is linked for this profile yet. */
    NO_TARGET,
    /** A window is linked but the IDE could not be reached (closed / restarted / wrong port). */
    UNREACHABLE,
    /** The IDE was reached but could not resolve the requested class/method/source. */
    NOT_RESOLVED
}
