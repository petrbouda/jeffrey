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

import WorkspaceEvent from './model/WorkspaceEvent';
import WorkspaceEventType from './model/WorkspaceEventType';

class WorkspaceEventClient {
    private static generateMockEvents(): WorkspaceEvent[] {
        const now = Date.now();
        return [
            {
                eventId: 1,
                originEventId: "evt_001",
                projectId: "proj_abc123",
                workspaceId: "workspace_1",
                eventType: WorkspaceEventType.PROJECT_CREATED,
                content: JSON.stringify({
                    projectName: "CPU Performance Analysis",
                    repositoryType: "ASPROF_FILE",
                    attributes: {
                        cluster: "blue",
                        namespace: "klingon",
                        environment: "production",
                        version: "1.0.0"
                    }
                }),
                originCreatedAt: now - 300000, // 5 minutes ago
                createdAt: now - 300000
            },
            {
                eventId: 2,
                originEventId: "evt_002",
                projectId: "proj_abc123",
                workspaceId: "workspace_1",
                eventType: WorkspaceEventType.SESSION_CREATED,
                content: JSON.stringify({
                    sessionId: "sess_abc123def456",
                    relativePath: "workspace1/project1/session1",
                    workspacesPath: "/var/lib/jeffrey/workspaces"
                }),
                originCreatedAt: now - 120000, // 2 minutes ago
                createdAt: now - 120000
            },
            {
                eventId: 3,
                originEventId: "evt_003",
                projectId: "proj_def456",
                workspaceId: "workspace_1",
                eventType: WorkspaceEventType.PROJECT_CREATED,
                content: JSON.stringify({
                    projectName: "Memory Leak Detection",
                    repositoryType: "ASPROF_TEMP_FILE",
                    attributes: {
                        cluster: "red",
                        namespace: "vulcan",
                        environment: "development",
                        version: "2.1.0",
                        region: "us-west-2"
                    }
                }),
                originCreatedAt: now - 600000, // 10 minutes ago
                createdAt: now - 600000
            },
            {
                eventId: 4,
                originEventId: "evt_004",
                projectId: "proj_def456",
                workspaceId: "workspace_1",
                eventType: WorkspaceEventType.SESSION_CREATED,
                content: JSON.stringify({
                    sessionId: "sess_def456ghi789",
                    relativePath: "workspace1/project2/session1",
                    workspacesPath: "/var/lib/jeffrey/workspaces"
                }),
                originCreatedAt: now - 480000, // 8 minutes ago
                createdAt: now - 480000
            },
            {
                eventId: 5,
                originEventId: "evt_005",
                projectId: "proj_old789",
                workspaceId: "workspace_1",
                eventType: WorkspaceEventType.PROJECT_DELETED,
                content: JSON.stringify({
                    projectName: "Old Performance Test",
                    deletedAt: new Date(now - 900000).toISOString(), // 15 minutes ago
                    reason: "cleanup"
                }),
                originCreatedAt: now - 900000,
                createdAt: now - 900000
            },
            {
                eventId: 6,
                originEventId: "evt_006",
                projectId: "proj_abc123",
                workspaceId: "workspace_1",
                eventType: WorkspaceEventType.SESSION_DELETED,
                content: JSON.stringify({
                    sessionId: "sess_old123abc",
                    deletedAt: new Date(now - 360000).toISOString(), // 6 minutes ago
                    reason: "expired"
                }),
                originCreatedAt: now - 360000,
                createdAt: now - 360000
            },
            {
                eventId: 7,
                originEventId: "evt_007",
                projectId: "proj_ghi789",
                workspaceId: "workspace_2",
                eventType: WorkspaceEventType.PROJECT_CREATED,
                content: JSON.stringify({
                    projectName: "Network I/O Analysis",
                    repositoryType: "ASPROF_FILE",
                    attributes: {
                        cluster: "green",
                        namespace: "romulan",
                        environment: "staging",
                        datacenter: "dc-east",
                        team: "performance"
                    }
                }),
                originCreatedAt: now - 1800000, // 30 minutes ago
                createdAt: now - 1800000
            },
        ];
    }

    static async list(workspaceId?: string): Promise<WorkspaceEvent[]> {
        // Simulate API delay
        await new Promise(resolve => setTimeout(resolve, 500));
        
        const allEvents = this.generateMockEvents();
        
        if (workspaceId) {
            return allEvents.filter(event => event.workspaceId === workspaceId);
        }
        
        return allEvents;
    }

    static async getById(eventId: number): Promise<WorkspaceEvent | null> {
        // Simulate API delay
        await new Promise(resolve => setTimeout(resolve, 200));
        
        const events = this.generateMockEvents();
        return events.find(event => event.eventId === eventId) || null;
    }

    // TODO: Replace with actual API calls when backend is ready
    // static async list(workspaceId?: string): Promise<WorkspaceEvent[]> {
    //     const url = workspaceId 
    //         ? `/api/workspaces/${workspaceId}/events`
    //         : '/api/workspace-events';
    //     
    //     const response = await fetch(url);
    //     if (!response.ok) {
    //         throw new Error(`Failed to fetch workspace events: ${response.statusText}`);
    //     }
    //     return response.json();
    // }
}

export default WorkspaceEventClient;