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

package cafe.jeffrey.hub.core.streaming;

/**
 * The workspace, project or session a replay was asked for does not exist.
 *
 * <p>A domain exception rather than a gRPC status: this travels through the activity service, whose
 * worker would otherwise store {@code "NOT_FOUND: ..."} as a scan's error and hand that wire spelling
 * to an MCP client. {@code GrpcExceptions.toStatus} turns it into NOT_FOUND at the boundary.</p>
 */
public class ReplayScopeNotFoundException extends RuntimeException {

    public ReplayScopeNotFoundException(String message) {
        super(message);
    }
}
