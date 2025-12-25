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

import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';
import WorkspaceEventType from '@/services/api/model/WorkspaceEventType';

export interface ProjectCreatedContent {
    projectName: string;
    repositoryType: string;
    attributes: Record<string, string>;
}

export interface SessionCreatedContent {
    sessionId: string;
    relativePath: string;
    workspacesPath: string;
}

export interface ProjectDeletedContent {
    projectName: string;
    deletedAt: string;
    reason?: string;
}

export interface SessionDeletedContent {
    sessionId: string;
    deletedAt: string;
    reason?: string;
}

export class EventContentParser {
    static parseContent(event: WorkspaceEvent): any {
        try {
            return JSON.parse(event.content);
        } catch (error) {
            console.warn('Failed to parse event content:', error);
            return { raw: event.content };
        }
    }

    static getEventDescription(event: WorkspaceEvent): string {
        const content = this.parseContent(event);

        switch (event.eventType) {
            case WorkspaceEventType.PROJECT_CREATED:
                return `Project "${content.projectName}" created with ${content.repositoryType} repository`;
            case WorkspaceEventType.SESSION_CREATED:
                return `Session "${content.sessionId}" created in ${content.relativePath}`;
            case WorkspaceEventType.PROJECT_DELETED:
                return `Project "${content.projectName}" deleted`;
            case WorkspaceEventType.SESSION_DELETED:
                return `Session "${content.sessionId}" deleted`;
            default:
                return 'Unknown event type';
        }
    }

    static getEventIcon(eventType: WorkspaceEventType): string {
        switch (eventType) {
            case WorkspaceEventType.PROJECT_CREATED:
                return 'bi-folder-plus';
            case WorkspaceEventType.PROJECT_DELETED:
                return 'bi-folder-x';
            case WorkspaceEventType.SESSION_CREATED:
                return 'bi-play-circle';
            case WorkspaceEventType.SESSION_DELETED:
                return 'bi-stop-circle';
            default:
                return 'bi-question-circle';
        }
    }

    static getEventColor(eventType: WorkspaceEventType): string {
        switch (eventType) {
            case WorkspaceEventType.PROJECT_CREATED:
                return 'success';
            case WorkspaceEventType.PROJECT_DELETED:
                return 'danger';
            case WorkspaceEventType.SESSION_CREATED:
                return 'primary';
            case WorkspaceEventType.SESSION_DELETED:
                return 'warning';
            default:
                return 'secondary';
        }
    }

    static getEventDisplayName(eventType: WorkspaceEventType): string {
        switch (eventType) {
            case WorkspaceEventType.PROJECT_CREATED:
                return 'Project Created';
            case WorkspaceEventType.PROJECT_DELETED:
                return 'Project Deleted';
            case WorkspaceEventType.SESSION_CREATED:
                return 'Session Created';
            case WorkspaceEventType.SESSION_DELETED:
                return 'Session Deleted';
            default:
                return 'Unknown Event';
        }
    }
}