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

package cafe.jeffrey.hub.core.activity;

/**
 * The Hub could not admit a scan because it has no room for one: every retained slot holds a scan
 * that is still running, or the service is shutting down.
 *
 * <p>Its own type rather than an {@link IllegalStateException}, because the gRPC boundary answers it
 * with {@code RESOURCE_EXHAUSTED} — a status that tells the caller to wait or cancel and try again.
 * Any other {@code IllegalStateException} that {@link HubActivityService#start} lets through comes from
 * resolving the scope (a repository read, a directory listing) and is not a capacity problem; a caller
 * told to wait for one of those would wait forever.</p>
 */
public final class ActivityCapacityException extends RuntimeException {

    public ActivityCapacityException(String message) {
        super(message);
    }
}
