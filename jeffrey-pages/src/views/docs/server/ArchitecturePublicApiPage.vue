<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'purpose', text: 'Purpose', level: 2 },
  { id: 'grpc-services', text: 'gRPC Services', level: 2 },
  { id: 'use-case', text: 'Use Case', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="gRPC API"
        icon="bi bi-hdd-network"
      />

      <div class="docs-content">
        <p>Jeffrey Server exposes gRPC services for Jeffrey Local instances to connect and fetch data. All communication between Jeffrey Local and Jeffrey Server uses gRPC on port <code>9090</code> (configurable).</p>

        <DocsCallout type="info">
          <strong>Remote Connectivity:</strong> Jeffrey Local connects to Jeffrey Server via gRPC to browse workspaces, projects, instances, download recordings, and manage profiler settings. All CPU-intensive analysis happens locally.
        </DocsCallout>

        <h2 id="purpose">Purpose</h2>
        <p>The gRPC API enables Jeffrey Local to communicate with Jeffrey Server for remote workspace operations.</p>

        <div class="purpose-cards">
          <div class="purpose-card">
            <div class="card-icon"><i class="bi bi-broadcast"></i></div>
            <div class="card-content">
              <h4>Efficient Protocol</h4>
              <p>gRPC with Protocol Buffers for fast, type-safe communication between applications</p>
            </div>
          </div>
          <div class="purpose-card">
            <div class="card-icon"><i class="bi bi-cloud-download"></i></div>
            <div class="card-content">
              <h4>Recording Streaming</h4>
              <p>Stream JFR recordings and artifacts in 64KB chunks for efficient transfer</p>
            </div>
          </div>
          <div class="purpose-card">
            <div class="card-icon"><i class="bi bi-sliders"></i></div>
            <div class="card-content">
              <h4>Settings Sync</h4>
              <p>Manage profiler settings hierarchy across workspace, project, and global levels</p>
            </div>
          </div>
        </div>

        <h2 id="grpc-services">gRPC Services</h2>
        <p>Jeffrey Server exposes 8 gRPC services defined in <code>shared/server-api/src/main/proto/jeffrey/server/api/v1/</code>.</p>

        <div class="endpoint-groups">
          <!-- WorkspaceService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-house"></i>
              <h4>WorkspaceService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetApiInfo</code>
                </div>
                <p>Returns API and Jeffrey version</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListWorkspaces</code>
                </div>
                <p>List all available workspaces</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetWorkspace</code>
                </div>
                <p>Get workspace details by ID</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteWorkspace</code>
                </div>
                <p>Delete workspace and all its data</p>
              </div>
            </div>
          </div>

          <!-- ProjectService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-folder"></i>
              <h4>ProjectService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListProjects</code>
                </div>
                <p>List projects in workspace</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetProject</code>
                </div>
                <p>Get project details</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteProject</code>
                </div>
                <p>Delete project by ID</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>RestoreProject</code>
                </div>
                <p>Restore a previously deleted project</p>
              </div>
            </div>
          </div>

          <!-- InstanceService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-server"></i>
              <h4>InstanceService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListInstances</code>
                </div>
                <p>List instances/pods for project</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetInstance</code>
                </div>
                <p>Get instance details</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListInstanceSessions</code>
                </div>
                <p>List sessions for an instance</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetInstanceDetail</code>
                </div>
                <p>Instance identity, sessions, and filesystem-backed storage stats (file count, total size)</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetInstanceSessionDetail</code>
                </div>
                <p>Per-session detail: session metadata plus the JFR one-shot environment events from the session's latest finished chunk, serialised as a JSON map keyed by event type name (<code>jdk.JVMInformation</code>, <code>jdk.GCConfiguration</code>, <code>jdk.Shutdown</code>, …)</p>
              </div>
            </div>
          </div>

          <!-- RepositoryService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-collection"></i>
              <h4>RepositoryService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListSessions</code>
                </div>
                <p>List all recording sessions</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetSession</code>
                </div>
                <p>Get session details</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetRepositoryStatistics</code>
                </div>
                <p>Get repository statistics</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteSession</code>
                </div>
                <p>Delete recording session</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteFilesInSession</code>
                </div>
                <p>Delete specific files in session</p>
              </div>
            </div>
          </div>

          <!-- RecordingDownloadService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-cloud-download"></i>
              <h4>RecordingDownloadService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DownloadMergedRecordings</code>
                </div>
                <p>Stream merged recordings in 64KB chunks</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DownloadArtifactFile</code>
                </div>
                <p>Stream artifact file - heap dump, logs</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DownloadRecordingFile</code>
                </div>
                <p>Stream single recording file</p>
              </div>
            </div>
          </div>

          <!-- ProfilerSettingsService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-sliders"></i>
              <h4>ProfilerSettingsService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetSettings</code>
                </div>
                <p>Get effective profiler settings</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>UpsertSettings</code>
                </div>
                <p>Create/update project settings</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteSettings</code>
                </div>
                <p>Delete project settings</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListAllSettings</code>
                </div>
                <p>List settings at all levels</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>UpsertSettingsAtLevel</code>
                </div>
                <p>Create/update at any level</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteSettingsAtLevel</code>
                </div>
                <p>Delete at any level</p>
              </div>
            </div>
          </div>

          <!-- EventStreamingService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-broadcast"></i>
              <h4>EventStreamingService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>SubscribeEvents</code>
                  <span class="rpc-type">server-streaming</span>
                </div>
                <p>Subscribe to live JFR events from a session's streaming repository with event type filtering and time range</p>
              </div>
            </div>
          </div>

          <!-- WorkspaceEventsService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-journal-text"></i>
              <h4>WorkspaceEventsService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetWorkspaceEvents</code>
                </div>
                <p>Get events for a workspace with optional event type filter</p>
              </div>
            </div>
          </div>
        </div>

        <h2 id="use-case">Use Case</h2>
        <p>The typical workflow for Remote workspace connectivity:</p>

        <div class="workflow-diagram">
          <div class="workflow-row">
            <div class="workflow-box local">
              <div class="box-header">
                <i class="bi bi-pc-display"></i>
                <span>Developer Machine</span>
              </div>
              <div class="box-content">
                <strong>Jeffrey Local</strong>
                <p>Connects to Jeffrey Server via gRPC</p>
              </div>
            </div>
            <div class="workflow-arrow">
              <span>gRPC</span>
              <i class="bi bi-arrow-right"></i>
            </div>
            <div class="workflow-box cloud">
              <div class="box-header">
                <i class="bi bi-cloud"></i>
                <span>Server / Kubernetes</span>
              </div>
              <div class="box-content">
                <strong>Jeffrey Server</strong>
                <p>Collects recordings, exposes gRPC services</p>
              </div>
            </div>
          </div>
        </div>

        <h3>Benefits</h3>
        <ul>
          <li><strong>Cost Savings</strong> - Expensive profile analysis runs on local machine, not in cloud</li>
          <li><strong>Data Access</strong> - Recordings collected in production streamed to local via gRPC</li>
          <li><strong>Security</strong> - Read-only data access with TLS support for secure communication</li>
          <li><strong>Efficiency</strong> - Protocol Buffers provide fast, compact serialization for large recording transfers</li>
        </ul>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Purpose Cards */
.purpose-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.purpose-card {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.card-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon i {
  font-size: 1.1rem;
  color: #5e64ff;
}

.card-content {
  flex: 1;
}

.card-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.card-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

/* Endpoint Groups */
.endpoint-groups {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.endpoint-group {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.group-header i {
  font-size: 1rem;
  color: #5e64ff;
}

.group-header h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.group-body {
  background: #fff;
}

.endpoint-item {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
}

.endpoint-item:last-child {
  border-bottom: none;
}

.endpoint-line {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.25rem;
}

.endpoint-line code {
  font-size: 0.8rem;
  color: #374151;
  background: #f1f5f9;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
}

.endpoint-item p {
  margin: 0;
  font-size: 0.8rem;
  color: #6b7280;
  padding-left: 3.5rem;
}

.method {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  min-width: 52px;
  text-align: center;
}

.method.get { background: #d1fae5; color: #065f46; }
.method.post { background: #dbeafe; color: #1e40af; }
.method.put { background: #fef3c7; color: #92400e; }
.method.delete { background: #fee2e2; color: #991b1b; }
.method.rpc { background: #ede9fe; color: #6d28d9; }

/* Workflow Diagram */
.workflow-diagram {
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.workflow-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
}

.workflow-box {
  min-width: 200px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.box-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.workflow-box.local .box-header i { color: #10b981; }
.workflow-box.cloud .box-header i { color: #5e64ff; }

.box-header span {
  font-size: 0.8rem;
  color: #5e6e82;
}

.box-content {
  padding: 1rem;
}

.box-content strong {
  display: block;
  font-size: 0.9rem;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.box-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

.workflow-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  color: #6c757d;
}

.workflow-arrow span {
  font-size: 0.7rem;
}

.workflow-arrow i {
  font-size: 1.25rem;
}

@media (max-width: 768px) {
  .purpose-cards {
    grid-template-columns: 1fr;
  }

  .workflow-row {
    flex-direction: column;
  }

  .workflow-arrow {
    transform: rotate(90deg);
  }
}
</style>
