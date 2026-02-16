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

import cafe.jeffrey.jfr.events.message.ImportantMessageEvent;
import cafe.jeffrey.jfr.events.message.Severity;

public abstract class JfrEmitter {

    // ==================== PROJECT events (HIGH severity) ====================

    public static void projectCreated(String projectName, String projectId) {
        emit(MessageType.PROJECT_CREATED,
                "Project '" + projectName + "' has been created (projectId=" + projectId + ")",
                Severity.HIGH, MessageCategory.PROJECT, false);
    }

    public static void projectDeleted(String projectId) {
        emit(MessageType.PROJECT_DELETED,
                "Project has been deleted (projectId=" + projectId + ")",
                Severity.HIGH, MessageCategory.PROJECT, false);
    }

    // ==================== INSTANCE events (MEDIUM severity) ====================

    public static void instanceCreated(String instanceId, String projectName, String projectId) {
        emit(MessageType.INSTANCE_CREATED,
                "New instance '" + instanceId + "' started for project '" + projectName + "' (projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.INSTANCE, false);
    }

    public static void instanceFinished(String instanceId, String projectId) {
        emit(MessageType.INSTANCE_FINISHED,
                "Instance '" + instanceId + "' finished (projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.INSTANCE, false);
    }

    public static void instanceAutoFinished(String instanceId, String projectId) {
        emit(MessageType.INSTANCE_AUTO_FINISHED,
                "Instance '" + instanceId + "' auto-finished, no active sessions remaining (projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.INSTANCE, false);
    }

    // ==================== SESSION events ====================

    public static void sessionCreated(String instanceId, int order, String projectId) {
        emit(MessageType.SESSION_CREATED,
                "New recording session #" + order + " started for instance '" + instanceId + "' (projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.SESSION, false);
    }

    public static void sessionFinished(String sessionId, String projectId) {
        emit(MessageType.SESSION_FINISHED,
                "Recording session finished (sessionId=" + sessionId + " projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.SESSION, false);
    }

    public static void sessionDeleted(String sessionId, String projectId) {
        emit(MessageType.SESSION_DELETED,
                "Recording session deleted (sessionId=" + sessionId + " projectId=" + projectId + ")",
                Severity.MEDIUM, MessageCategory.SESSION, false);
    }

    public static void sessionsCleaned(String projectName, int count) {
        emit(MessageType.SESSIONS_CLEANED,
                "Cleaned up " + count + " expired recording sessions from project '" + projectName + "'",
                Severity.LOW, MessageCategory.SESSION, false);
    }

    // ==================== RECORDING events (LOW severity) ====================

    public static void recordingFileCreated(String projectId, String sessionId, long originalSize, long compressedSize) {
        emit(MessageType.RECORDING_FILE_CREATED,
                "Recording file compressed (original=" + originalSize + " compressed=" + compressedSize +
                        " sessionId=" + sessionId + " projectId=" + projectId + ")",
                Severity.LOW, MessageCategory.RECORDING, false);
    }

    // ==================== ALERTS (isAlert=true) ====================

    public static void jvmCrashDetected(String sessionId, String instanceId, String projectId) {
        emit(MessageType.JVM_CRASH_DETECTED,
                "Session finished due to HotSpot JVM error — hs_err log detected" +
                        " (sessionId=" + sessionId + " instanceId=" + instanceId + " projectId=" + projectId + ")",
                Severity.CRITICAL, MessageCategory.SESSION, true);
    }

    public static void eventProcessingFailed(String eventType, String projectId, String errorMessage) {
        emit(MessageType.EVENT_PROCESSING_FAILED,
                "Failed to process workspace event: " + errorMessage +
                        " (eventType=" + eventType + " projectId=" + projectId + ")",
                Severity.HIGH, MessageCategory.SYSTEM, true);
    }

    // ==================== Private helper ====================

    private static void emit(MessageType type, String message,
                             Severity severity, MessageCategory category, boolean isAlert) {
        ImportantMessageEvent event = new ImportantMessageEvent();
        event.type = type.name();
        event.title = type.title();
        event.message = message;
        event.severity = severity;
        event.category = category.name();
        event.source = "jeffrey-platform";
        event.isAlert = isAlert;
        event.commit();
    }
}
