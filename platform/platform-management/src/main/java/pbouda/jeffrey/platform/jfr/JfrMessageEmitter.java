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

import cafe.jeffrey.jfr.events.message.AlertEvent;
import cafe.jeffrey.jfr.events.message.MessageEvent;
import cafe.jeffrey.jfr.events.message.Severity;

public abstract class JfrMessageEmitter {

    // ==================== PROJECT events (HIGH severity) ====================

    public static void projectCreated(String projectName, String projectId) {
        emitMessage(MessageType.PROJECT_CREATED,
                "Project '" + projectName + "' has been created (projectId=" + projectId + ")",
                Severity.HIGH, MessageCategory.PROJECT);
    }

    public static void projectDeleted(String projectId) {
        emitMessage(MessageType.PROJECT_DELETED,
                "Project has been deleted (projectId=" + projectId + ")",
                Severity.HIGH, MessageCategory.PROJECT);
    }

    // ==================== INSTANCE events (MEDIUM severity) ====================

    public static void instanceCreated(String instanceId, String projectName, String projectId) {
        emitMessage(MessageType.INSTANCE_CREATED,
                "New instance '" + instanceId + "' started for project '" + projectName + "' (projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.INSTANCE);
    }

    // ==================== SESSION events ====================

    public static void sessionCreated(String instanceId, int order, String projectId) {
        emitMessage(MessageType.SESSION_CREATED,
                "New recording session #" + order + " started for instance '" + instanceId + "' (projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.SESSION);
    }

    public static void sessionFinished(String sessionId, String projectId) {
        emitMessage(MessageType.SESSION_FINISHED,
                "Recording session finished (sessionId=" + sessionId + " projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.SESSION);
    }

    public static void sessionDeleted(String sessionId, String projectId) {
        emitMessage(MessageType.SESSION_DELETED,
                "Recording session deleted (sessionId=" + sessionId + " projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.SESSION);
    }

    public static void sessionsCleaned(String projectName, int count) {
        emitMessage(MessageType.SESSIONS_CLEANED,
                "Cleaned up " + count + " expired recording sessions from project '" + projectName + "'",
                Severity.LOW, MessageCategory.SESSION);
    }

    // ==================== ALERTS ====================

    public static void jvmCrashDetected(String sessionId, String instanceId, String projectId) {
        emitAlert(MessageType.JVM_CRASH_DETECTED,
                "Session finished due to HotSpot JVM error — hs_err log detected" +
                        " (sessionId=" + sessionId + " instanceId=" + instanceId + " projectId=" + projectId + ")",
                Severity.CRITICAL, MessageCategory.SESSION);
    }

    public static void eventProcessingFailed(String eventType, String projectId, String errorMessage) {
        emitAlert(MessageType.EVENT_PROCESSING_FAILED,
                "Failed to process workspace event: " + errorMessage +
                        " (eventType=" + eventType + " projectId=" + projectId + ")",
                Severity.HIGH, MessageCategory.SYSTEM);
    }

    // ==================== Private helpers ====================

    private static void emitMessage(MessageType type, String message, Severity severity, MessageCategory category) {
        MessageEvent event = new MessageEvent();
        event.type = type.name();
        event.title = type.title();
        event.message = message;
        event.severity = severity.name();
        event.category = category.name();
        event.source = "jeffrey-platform";
        event.commit();
    }

    private static void emitAlert(MessageType type, String message, Severity severity, MessageCategory category) {
        AlertEvent event = new AlertEvent();
        event.type = type.name();
        event.title = type.title();
        event.message = message;
        event.severity = severity.name();
        event.category = category.name();
        event.source = "jeffrey-platform";
        event.commit();
    }
}
