/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.jfr;

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.notification.Severity;

/**
 * Emits the hub's own JFR notification events, one per thing that happened, each with a message
 * in the same {@code key=value} shape the logs use so that a reader of the recording and a
 * reader of the log look for the same words.
 */
public final class JfrNotificationEmitter {

    private static final String SOURCE = "jeffrey-hub";

    private JfrNotificationEmitter() {
    }

    // ==================== PROJECT events (HIGH severity) ====================

    public static void projectCreated(String projectName, String projectId) {
        emit(NotificationType.PROJECT_CREATED, Severity.HIGH, NotificationCategory.PROJECT,
                "Project created", kv("project_name", projectName), kv("project_id", projectId));
    }

    public static void projectDeleted(String projectId) {
        emit(NotificationType.PROJECT_DELETED, Severity.HIGH, NotificationCategory.PROJECT,
                "Project deleted", kv("project_id", projectId));
    }

    // ==================== INSTANCE events (MEDIUM severity) ====================

    public static void instanceCreated(String instanceId, String projectName, String projectId) {
        emit(NotificationType.INSTANCE_CREATED, Severity.MEDIUM, NotificationCategory.INSTANCE,
                "New instance started",
                kv("instance_id", instanceId), kv("project_name", projectName), kv("project_id", projectId));
    }

    // ==================== SESSION events ====================

    public static void sessionCreated(String sessionId, String instanceId, int order, String projectId) {
        emit(NotificationType.SESSION_CREATED, Severity.LOW, NotificationCategory.SESSION,
                "New recording session started",
                kv("session_id", sessionId), kv("instance_id", instanceId), kv("order", order), kv("project_id", projectId));
    }

    public static void sessionFinished(String sessionId, String projectId) {
        emit(NotificationType.SESSION_FINISHED, Severity.LOW, NotificationCategory.SESSION,
                "Recording session finished", kv("session_id", sessionId), kv("project_id", projectId));
    }

    public static void sessionDeleted(String sessionId, String projectId) {
        emit(NotificationType.SESSION_DELETED, Severity.LOW, NotificationCategory.SESSION,
                "Recording session deleted", kv("session_id", sessionId), kv("project_id", projectId));
    }

    public static void sessionsCleaned(String projectName, int count) {
        emit(NotificationType.SESSIONS_CLEANED, Severity.LOW, NotificationCategory.SESSION,
                "Cleaned up expired recording sessions", kv("project_name", projectName), kv("count", count));
    }

    // ==================== Private helpers ====================

    private static String kv(String key, Object value) {
        return key + "=" + value;
    }

    private static void emit(
            NotificationType type, Severity severity, NotificationCategory category, String what, String... attributes) {
        NotificationEvent event = new NotificationEvent();
        event.type = type.name();
        event.message = what + ": " + String.join(" ", attributes);
        event.severity = severity.name();
        event.category = category.name();
        event.source = SOURCE;
        event.emit();
    }
}
