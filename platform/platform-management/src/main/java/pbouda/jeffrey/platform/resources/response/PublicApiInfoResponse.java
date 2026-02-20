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

package pbouda.jeffrey.platform.resources.response;

/**
 * Response for the {@code GET /api/public/info} endpoint.
 *
 * @param version    the Jeffrey build version string (e.g. "1.2.3")
 * @param apiVersion monotonically increasing integer representing the public API contract version
 */
public record PublicApiInfoResponse(String version, int apiVersion) {

    /**
     * Current public API version. Increment this whenever the public API contract
     * changes in a way that could break remote clients.
     */
    public static final int CURRENT_API_VERSION = 1;
}
