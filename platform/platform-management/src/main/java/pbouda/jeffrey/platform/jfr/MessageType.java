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

package pbouda.jeffrey.platform.jfr;

public enum MessageType {
    PROJECT_CREATED("Project Created"),
    PROJECT_DELETED("Project Deleted"),
    INSTANCE_CREATED("Instance Started"),
    SESSION_CREATED("Recording Session Started"),
    SESSION_FINISHED("Recording Session Finished"),
    SESSION_DELETED("Recording Session Deleted"),
    SESSIONS_CLEANED("Sessions Cleaned Up"),
    JVM_CRASH_DETECTED("JVM Crash Detected"),
    EVENT_PROCESSING_FAILED("Event Processing Failed");

    private final String title;

    MessageType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
