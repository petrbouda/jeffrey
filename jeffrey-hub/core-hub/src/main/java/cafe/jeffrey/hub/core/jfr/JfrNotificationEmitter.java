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

import cafe.jeffrey.jfr.events.notification.NotificationEvent;
import cafe.jeffrey.jfr.events.notification.Severity;

public abstract class JfrNotificationEmitter {

    // ==================== PROJECT events (HIGH severity) ====================

    public static void projectCreated(String projectName, String projectId) {
        emit(NotificationType.PROJECT_CREATED,
                "Project created: projectName=" + projectName + " projectId=" + projectId,
                Severity.HIGH, NotificationCategory.PROJECT);
    }

    public static void projectDeleted(String projectId) {
        emit(NotificationType.PROJECT_DELETED,
                "Project deleted: projectId=" + projectId,
                Severity.HIGH, NotificationCategory.PROJECT);
    }

    // ==================== INSTANCE events (MEDIUM severity) ====================

    public static void instanceCreated(String instanceId, String projectName, String projectId) {
        emit(NotificationType.INSTANCE_CREATED,
                "New instance started: instanceId=" + instanceId + " projectName=" + projectName + " projectId=" + projectId,
                Severity.MEDIUM, NotificationCategory.INSTANCE);
    }

    // ==================== SESSION events ====================

    public static void sessionCreated(String sessionId, String instanceId, int order, String projectId) {
        emit(NotificationType.SESSION_CREATED,
                "New recording session started: sessionId=" + sessionId + " instanceId=" + instanceId + " order=" + order + " projectId=" + projectId,
                Severity.LOW, NotificationCategory.SESSION);
    }

    public static void sessionFinished(String sessionId, String projectId) {
        emit(NotificationType.SESSION_FINISHED,
                "Recording session finished: sessionId=" + sessionId + " projectId=" + projectId,
                Severity.LOW, NotificationCategory.SESSION);
    }

    public static void sessionDeleted(String sessionId, String projectId) {
        emit(NotificationType.SESSION_DELETED,
                "Recording session deleted: sessionId=" + sessionId + " projectId=" + projectId,
                Severity.LOW, NotificationCategory.SESSION);
    }

    public static void sessionsCleaned(String projectName, int count) {
        emit(NotificationType.SESSIONS_CLEANED,
                "Cleaned up expired recording sessions: projectName=" + projectName + " count=" + count,
                Severity.LOW, NotificationCategory.SESSION);
    }

    // ==================== CRITICAL and HIGH ====================

    public static void jvmCrashDetected(String sessionId, String instanceId, String projectId) {
        emit(NotificationType.JVM_CRASH_DETECTED,
                "Session finished due to HotSpot JVM error, hs_err log detected: sessionId=" + sessionId + " instanceId=" + instanceId + " projectId=" + projectId,
                Severity.CRITICAL, NotificationCategory.SESSION);
    }

    public static void eventProcessingFailed(String eventType, String projectId, String errorMessage) {
        emit(NotificationType.EVENT_PROCESSING_FAILED,
                "Failed to process workspace event: eventType=" + eventType + " projectId=" + projectId + " error=" + errorMessage,
                Severity.HIGH, NotificationCategory.SYSTEM);
    }

    // ==================== Private helpers ====================

    private static void emit(NotificationType type, String message, Severity severity, NotificationCategory category) {
        NotificationEvent event = new NotificationEvent();
        event.type = type.name();
        event.message = message;
        event.severity = severity.name();
        event.category = category.name();
        event.source = "jeffrey-platform";
        event.emit();
    }
}
