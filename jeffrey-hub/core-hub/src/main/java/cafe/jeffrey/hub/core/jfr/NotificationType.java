/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.hub.core.jfr;

/**
 * The kinds of notification the hub raises.
 *
 * <p>The constant's own name is what reaches the recording, so these are the vocabulary a reader
 * groups and filters by — and the notification's name as well, since a notification carries no
 * separate label.
 */
public enum NotificationType {
    PROJECT_CREATED,
    PROJECT_DELETED,
    INSTANCE_CREATED,
    SESSION_CREATED,
    SESSION_FINISHED,
    SESSION_DELETED,
    SESSIONS_CLEANED,
    JVM_CRASH_DETECTED,
    EVENT_PROCESSING_FAILED
}
