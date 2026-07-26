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
import FormattingService from '@shared/services/FormattingService';

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

export interface ProjectInstanceCreatedContent {
  relativeInstancePath: string;
}

export interface SessionFileCreatedContent {
  instanceId: string;
  sessionId: string;
  fileName: string;
  fileType: string;
  fileCategory: string;
  /**
   * Size when the file was first announced. A later LZ4 compression changes the file on disk
   * without producing a new event, so this is a first-sighting value rather than a live size.
   */
  sizeBytes: number;
  fileCreatedAt: number;
}

export interface InstanceLifecycleContent {
  instanceId: string;
  finishedAt?: number;
  expiredAt?: number;
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
      case WorkspaceEventType.PROJECT_INSTANCE_CREATED:
        return `Instance "${content.relativeInstancePath}" created`;
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED:
        return `Session "${content.sessionId}" created in ${content.relativePath}`;
      case WorkspaceEventType.PROJECT_DELETED:
        return `Project "${content.projectName}" deleted`;
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED:
        return `Session "${content.sessionId}" deleted`;
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED:
        return 'Session finished';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED:
        return `Recording "${content.fileName}" available (${FormattingService.formatBytes(content.sizeBytes)})`;
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED:
        return `Artifact "${content.fileName}" available (${FormattingService.formatBytes(content.sizeBytes)})`;
      case WorkspaceEventType.PROJECT_INSTANCE_FINISHED:
        return `Instance "${content.instanceId}" finished`;
      case WorkspaceEventType.PROJECT_INSTANCE_EXPIRED:
        return `Instance "${content.instanceId}" expired`;
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
      case WorkspaceEventType.PROJECT_INSTANCE_CREATED:
        return 'bi-box';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED:
        return 'bi-play-circle';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED:
        return 'bi-stop-circle';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED:
        return 'bi-stop-circle-fill';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED:
        return 'bi-file-earmark-bar-graph';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED:
        return 'bi-paperclip';
      case WorkspaceEventType.PROJECT_INSTANCE_FINISHED:
        return 'bi-box-seam';
      case WorkspaceEventType.PROJECT_INSTANCE_EXPIRED:
        return 'bi-hourglass-bottom';
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
      case WorkspaceEventType.PROJECT_INSTANCE_CREATED:
        return 'info';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED:
        return 'primary';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED:
        return 'warning';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED:
        return 'info';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED:
        return 'primary';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED:
        return 'purple';
      case WorkspaceEventType.PROJECT_INSTANCE_FINISHED:
        return 'info';
      case WorkspaceEventType.PROJECT_INSTANCE_EXPIRED:
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
      case WorkspaceEventType.PROJECT_INSTANCE_CREATED:
        return 'Instance Created';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED:
        return 'Session Created';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED:
        return 'Session Deleted';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED:
        return 'Session Finished';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED:
        return 'Recording File Created';
      case WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED:
        return 'Artifact File Created';
      case WorkspaceEventType.PROJECT_INSTANCE_FINISHED:
        return 'Instance Finished';
      case WorkspaceEventType.PROJECT_INSTANCE_EXPIRED:
        return 'Instance Expired';
      default:
        return 'Unknown Event';
    }
  }
}
