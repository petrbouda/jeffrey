<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
        <p>Jeffrey Hub exposes gRPC services for Jeffrey Microscope instances to connect and fetch data. All communication between Jeffrey Microscope and Jeffrey Hub uses gRPC on port <code>9090</code> (configurable).</p>

        <DocsCallout type="info">
          <strong>Remote Connectivity:</strong> Jeffrey Microscope connects to Jeffrey Hub via gRPC to browse workspaces, projects, instances and download recordings. Full profile analysis happens locally; <router-link to="/docs/microscope-mcp/tools#hubs">Microscope’s MCP tools</router-link> pull a session down by session, time window or file.
        </DocsCallout>

        <h2 id="purpose">Purpose</h2>
        <p>The gRPC API enables Jeffrey Microscope to communicate with Jeffrey Hub for remote workspace operations.</p>

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
        </div>

        <h2 id="grpc-services">gRPC Services</h2>
        <p>Jeffrey Hub exposes 5 gRPC services, 13 RPCs in all, defined in <code>shared/hub-api/src/main/proto/jeffrey/hub/api/v1/</code>. There is one session listing: an instance's sessions, a project's repository size and a session's detail are all read from it by Microscope rather than asked of the hub separately.</p>
        <p>Every id is read off the shared volume, which is the hub's only state: a workspace's id is its directory name &mdash; the reference id the provisioner's <code>project.workspace-id</code> names &mdash; a project's id is the <code>projectId</code> the provisioner wrote into its <code>.project-info.json</code>, and an instance's and a session's ids are their directory names. Every request below the workspace carries <code>workspace_id</code> and <code>project_id</code> (<code>ListInstances</code>, <code>GetInstance</code>, <code>DeleteProject</code>, <code>ListSessions</code>, <code>GetSession</code>, <code>DeleteSession</code>, <code>SetSessionRetained</code>, <code>DownloadFile</code>), so the hub never searches the whole volume for a session. An unknown workspace, project, instance or session is <code>NOT_FOUND</code>.</p>
        <p>Each call answers one level of the tree &mdash; workspaces, a workspace's projects, a project's instances, one instance with its sessions, one session with its files &mdash; so a screen asks for the level it shows and nothing below it. Files travel only with <code>GetSession</code>: every session in a listing carries its <code>file_count</code> and <code>size_bytes</code> and an empty <code>files</code>.</p>

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
                <p>Returns the Jeffrey version</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>ListWorkspaces</code>
                </div>
                <p>List all available workspaces. Microscope resolves a single workspace from this listing too</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>CreateWorkspace</code>
                </div>
                <p>Create a workspace with a user-supplied reference ID (used by the CLI to route recordings), which becomes its id and directory name. The hub writes the directory's <code>.workspace-info.json</code>; an existing directory of that name is adopted with its projects</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteWorkspace</code>
                </div>
                <p>Delete a workspace's directory with all its data</p>
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
                <p>List projects in workspace. Each <code>ProjectInfo</code> carries its workspace's id, which is also the workspace's reference id; its <code>status</code> and <code>session_count</code>, read off each instance's newest session alone (only the newest can still be recording); and <code>size_bytes</code>, what the last retention pass measured &mdash; absent before the first pass, never re-measured on request</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetProject</code>
                </div>
                <p>Get project details, NOT_FOUND unless the project belongs to the named workspace. Its <code>ProjectInfo</code> carries everything Microscope needs to open the project, so opening one costs this single call</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteProject</code>
                </div>
                <p>Delete a project for good: its directory on the workspace volume is gone before the response returns. There is no restore</p>
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
                <p>List a project's instances with their lifecycle and session count, without their sessions. The status is read off the instance's newest session, the only one that can still be recording: <code>PENDING</code> with no session, <code>ACTIVE</code> while the newest records, <code>FINISHED</code> otherwise (<code>finished_at</code> is the newest session's finish)</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetInstance</code>
                </div>
                <p>One instance with its sessions, newest first, each with its <code>file_count</code> and <code>size_bytes</code> but not its files. What an instance's page and the timeline's instance drawer read</p>
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
                <p>List a project's recording sessions, newest first. An optional <code>SessionFilter</code> narrows the listing on the hub: <code>active_from</code>/<code>active_to</code> keep the sessions that were recording at any point inside the window (a still-running session always matches a window reaching the present), <code>status</code> keeps one status and <code>limit</code> caps the count. Each session carries its <code>file_count</code> and <code>size_bytes</code> but not its files. One instance's sessions come from <code>InstanceService.GetInstance</code>. A session that finished without producing any data is <code>failed</code>; one that ended because it sent no heartbeat within the startup grace has <code>heartbeat_missing</code> set instead and is never <code>failed</code></p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>GetSession</code>
                </div>
                <p>One session with its files &mdash; the only call that sends files; Microscope fetches them when a session is opened. Microscope reads the session's environment (<code>jdk.JVMInformation</code>, <code>jdk.GCConfiguration</code>, <code>jdk.Shutdown</code>, …) by downloading its newest closed chunk and parsing it locally &mdash; the hub holds no JFR parser</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DeleteSession</code>
                </div>
                <p>Delete a recording session's directory with all its files, and its instance's directory when that was the instance's last session</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>SetSessionRetained</code>
                </div>
                <p>Retain a session (exempt from every retention rule) or release it again; the flag is a <code>.retained</code> file in the session's directory</p>
              </div>
            </div>
          </div>

          <!-- FileDownloadService -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-cloud-download"></i>
              <h4>FileDownloadService</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method rpc">RPC</span>
                  <code>DownloadFile</code>
                </div>
                <p>Stream one file of a session &mdash; a JFR chunk, a heap dump, an application log, the <code>gc.jvm-log</code>, a crash file, the perf-counters file &mdash; named by its <code>file_id</code>, in 64KB chunks. One RPC for every kind: what a file <em>is</em> decides what the caller does with it afterwards, and the caller read that off the listing that gave it the id. One file per call, always: a session's recording is several rotated chunk files, and Microscope fetches them separately and in parallel &mdash; the hub never joins them, so it never sees a selection of files and never has to judge one. The first chunk carries the file's <code>total_size</code> and the name it has on the hub <em>now</em>, because the compression job may have renamed it since the caller listed the session</p>
              </div>
            </div>
          </div>


        </div>

        <h2 id="use-case">Use Case</h2>
        <p>The typical workflow for Remote workspace connectivity:</p>

        <div class="workflow-diagram">
          <div class="workflow-row">
            <div class="workflow-box microscope">
              <div class="box-header">
                <i class="bi bi-pc-display"></i>
                <span>Developer Machine</span>
              </div>
              <div class="box-content">
                <strong>Jeffrey Microscope</strong>
                <p>Connects to Jeffrey Hub via gRPC</p>
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
                <strong>Jeffrey Hub</strong>
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

.badge-stream {
  display: inline-block;
  margin-left: 0.5rem;
  padding: 0.1rem 0.45rem;
  border-radius: 4px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
}

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
