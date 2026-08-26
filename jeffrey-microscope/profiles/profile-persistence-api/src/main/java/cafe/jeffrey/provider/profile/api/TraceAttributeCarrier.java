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

package cafe.jeffrey.provider.profile.api;

/**
 * What kind of thing carried an attribute — the two halves a trace is made of.
 * <p>
 * A key belongs to exactly one of these, decided by its {@link TraceAttributeSource}, and the carrier
 * is what tells every query which index table to read and what "the same one" means when a search is
 * scoped: the same span, or the same notification.
 * <p>
 * This is not a filter a caller chooses. It is derived from the key, so a condition can never be
 * pointed at the wrong table.
 */
public enum TraceAttributeCarrier {

    /** A span — an interval of work, with a name, an outcome and a place in the tree. */
    SPAN,

    /** A notification — an instant, which merely records the span that was open when it fired. */
    NOTIFICATION
}
