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

package pbouda.jeffrey.repository.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import pbouda.jeffrey.common.model.profile.EventStacktrace;
import pbouda.jeffrey.common.model.profile.EventThread;
import pbouda.jeffrey.common.model.profile.EventType;
import pbouda.jeffrey.common.model.profile.ProfileInfo;

public class ProfileRepository {

    private static final String INSERT_PROFILE = """
            INSERT INTO profile (
                id,
                name,
                project_id,
                created_at,
                started_at,
                finished_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_EVENT_TYPE = """
            INSERT INTO event_types (
                name,
                label,
                description,
                categories,
                source,
                subtype,
                samples,
                weight
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_STACKTRACE = """
            INSERT INTO stacktraces (
                stacktrace_id,
                thread_id,
                type,
                subtype,
                frames
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private static final String INSERT_THREAD = """
            INSERT INTO threads (
                thread_id,
                os_id,
                java_id,
                os_name,
                java_name,
                is_virtual
            ) VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertProfile(ProfileInfo profileInfo) {
        jdbcTemplate.update(
                INSERT_PROFILE,
                profileInfo.id(),
                profileInfo.name(),
                profileInfo.projectId(),
                profileInfo.createdAt().toEpochMilli(),
                profileInfo.startedAt().toEpochMilli(),
                profileInfo.finishedAt().toEpochMilli());
    }

    public void insertEventType(EventType eventType) {
        jdbcTemplate.update(
                INSERT_EVENT_TYPE,
                eventType.name(),
                eventType.label(),
                eventType.description(),
                eventType.categories(),
                eventType.source(),
                eventType.samples(),
                eventType.weight()
        );
    }

    public void insertStacktrace(EventStacktrace stacktrace) {
        jdbcTemplate.update(
                INSERT_STACKTRACE,
                stacktrace.stacktraceId(),
                stacktrace.type(),
                stacktrace.subtype(),
                stacktrace.frames());
    }

    public void insertThread(EventThread thread) {
        jdbcTemplate.update(
                INSERT_THREAD,
                thread.threadId(),
                thread.osId(),
                thread.osName(),
                thread.javaId(),
                thread.javaName(),
                thread.isVirtual());
    }
}
