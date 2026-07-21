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

package cafe.jeffrey.profile.panel;

/**
 * Read-time context for building panels. Primary vs differential is the only axis that changes a panel's
 * option defaults (differential drops the method-trace weight toggle and any primary-only thread-mode),
 * and it is known from which controller/endpoint served the request.
 *
 * @param primary true for the primary flamegraph, false for a differential (secondary) comparison
 */
public record PanelContext(boolean primary) {

    public static final PanelContext PRIMARY = new PanelContext(true);
    public static final PanelContext DIFFERENTIAL = new PanelContext(false);
}
