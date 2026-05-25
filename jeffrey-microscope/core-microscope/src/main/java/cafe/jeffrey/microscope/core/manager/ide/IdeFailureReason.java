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
