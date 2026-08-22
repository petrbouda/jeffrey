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

package cafe.jeffrey.jfr.events.servlet;

/**
 * What the exchange filter records beyond the request's shape.
 * <p>
 * Both capture flags default to <b>off</b>, and deliberately so: a recording is a file that gets
 * uploaded, shared and kept, and query strings routinely carry access tokens, e-mail addresses and
 * search terms. An instrumentation library that exfiltrated those by default would be a liability
 * dressed as a convenience. Turn them on knowingly, for an application whose parameters you know
 * are safe to keep.
 *
 * @param captureQueryParams record query-string parameters as a JSON object on the event
 * @param capturePathParams  record the route's template variables as a JSON object on the event
 */
public record HttpExchangeSettings(boolean captureQueryParams, boolean capturePathParams) {

    /**
     * Records neither — the safe default.
     */
    public static HttpExchangeSettings defaults() {
        return new HttpExchangeSettings(false, false);
    }
}
