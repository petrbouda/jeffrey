<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'storage', text: 'Storage', level: 2 },
  { id: 'jobs', text: 'Jobs', level: 2 },
  { id: 'job', text: 'One Job', level: 2 },
  { id: 'run-job', text: 'Run a Job Now', level: 2 },
  { id: 'health', text: 'Health Probes', level: 2 },
  { id: 'errors', text: 'Errors', level: 2 },
  { id: 'examples', text: 'Examples', level: 2 }
];

const storageRequest = `curl -s http://jeffrey-hub:8080/api/v1/storage`;

const storageResponse = `{
  "measuredAt": "2026-11-08T20:35:10Z",
  "totalBytes": 23622320128,
  "totalFiles": 1412,
  "workspaces": [
    {
      "id": "production",
      "totalBytes": 23622320128,
      "totalFiles": 1412,
      "projects": [
        {
          "id": "orders",
          "totalBytes": 15032385536,
          "totalFiles": 904,
          "lastActivityAt": "2026-11-08T20:30:00Z"
        },
        {
          "id": "payments",
          "totalBytes": 8589934592,
          "totalFiles": 508,
          "lastActivityAt": null
        }
      ]
    }
  ]
}`;

const storageBeforeFirstPass = `{
  "measuredAt": null,
  "totalBytes": 0,
  "totalFiles": 0,
  "workspaces": []
}`;

const jobsRequest = `curl -s http://jeffrey-hub:8080/api/v1/jobs`;

const jobsResponse = `[
  {
    "id": "project-retention",
    "enabled": true,
    "period": "PT5M",
    "manualRun": true,
    "params": {
      "session-retention": "7d",
      "max-sessions": "10",
      "active-session-file-retention": "7d",
      "max-size": "20G",
      "instance-retention": "14d"
    }
  },
  {
    "id": "repository-jfr-compression",
    "enabled": true,
    "period": "PT1M",
    "manualRun": false,
    "params": {}
  }
]`;

const jobRequest = `curl -s http://jeffrey-hub:8080/api/v1/jobs/project-retention`;

const runRequest = `curl -s -X POST http://jeffrey-hub:8080/api/v1/jobs/project-retention/run`;

const runResponse = `{
  "job": "project-retention",
  "startedAt": "2026-11-08T20:37:14.278Z",
  "durationMs": 412,
  "summary": "Deleted 3 sessions, 0 files and 1 instance; 22.0 GiB across 3 projects"
}`;

const healthRequest = `curl -s http://jeffrey-hub:8080/actuator/health/liveness
curl -s http://jeffrey-hub:8080/actuator/health/readiness`;

const notFoundResponse = `HTTP/1.1 404 Not Found

{
  "type": "CLIENT",
  "code": "SCHEDULER_JOB_NOT_FOUND",
  "message": "No such job: project-cleanup"
}`;

const conflictResponse = `HTTP/1.1 409 Conflict

{
  "type": "CLIENT",
  "code": "INVALID_REQUEST",
  "message": "Job cannot be run manually: repository-jfr-compression"
}`;

const biggestProjects = `curl -s http://jeffrey-hub:8080/api/v1/storage \\
  | jq -r '.workspaces[] | .id as $ws | .projects[] | "\\(.totalBytes)\\t\\($ws)/\\(.id)"' \\
  | sort -rn`;

const measureNow = `curl -s -X POST http://jeffrey-hub:8080/api/v1/jobs/project-retention/run | jq -r .summary
curl -s http://jeffrey-hub:8080/api/v1/storage | jq .measuredAt`;

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="REST API"
      icon="bi bi-braces"
    />

    <div class="docs-content">
      <p>
        Jeffrey Hub has no web UI. It serves a small JSON REST API on its HTTP port
        (<code>8080</code> by default, <code>server.port</code>) for operators and scripts: what every
        project occupies on the shared volume, the scheduler's jobs, and a way to run retention now.
        Microscope never calls it — it reads the volume itself.
      </p>

      <h2 id="overview">Overview</h2>
      <p>Every endpoint lives under <code>http://&lt;hub&gt;:8080/api/v1</code> and speaks JSON only.</p>
      <table>
        <thead>
          <tr>
            <th>Method</th>
            <th>Path</th>
            <th>What it returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>GET</code></td>
            <td><code>/api/v1/storage</code></td>
            <td>Size, files and last activity per workspace and project, as the last retention pass measured them</td>
          </tr>
          <tr>
            <td><code>GET</code></td>
            <td><code>/api/v1/jobs</code></td>
            <td>The scheduler's jobs and their settings, read-only</td>
          </tr>
          <tr>
            <td><code>GET</code></td>
            <td><code>/api/v1/jobs/{job}</code></td>
            <td>One job</td>
          </tr>
          <tr>
            <td><code>POST</code></td>
            <td><code>/api/v1/jobs/{job}/run</code></td>
            <td>Runs the job now and reports what it did</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning">
        <strong>No authentication.</strong> The API has no login and no token, and
        <code>POST /api/v1/jobs/{job}/run</code> deletes data exactly like a scheduled retention tick
        would. Keep the hub's Service inside the cluster; if you enable the Helm chart's Ingress,
        protect it in front of the hub.
      </DocsCallout>

      <h2 id="storage">Storage</h2>
      <p>
        <code>GET /api/v1/storage</code> returns what every project occupies on the volume, as
        measured by the last <code>project-retention</code> pass — totals for the whole volume, per
        workspace and per project. Ids are folder names: a workspace's id is its reference id, a
        project's is its directory name; there are no separate name fields.
      </p>

      <DocsCodeBlock
        language="bash"
        :code="storageRequest"
      />

      <DocsCodeBlock
        language="json"
        :code="storageResponse"
      />

      <ul>
        <li><code>measuredAt</code> — when the pass that produced these figures ran.</li>
        <li><code>totalBytes</code>, <code>totalFiles</code> — what is left on the volume after that pass.</li>
        <li><code>lastActivityAt</code> — the project's most recent activity, or <code>null</code> when it has none.</li>
      </ul>

      <DocsCallout type="info">
        <strong>The figures can be up to one retention period old</strong> — 5 minutes by default
        (<code>jeffrey.hub.scheduler.jobs.project-retention.period</code>). The first pass runs at
        startup; until it finishes, the endpoint answers with no figures:
      </DocsCallout>

      <DocsCodeBlock
        language="json"
        :code="storageBeforeFirstPass"
      />

      <p>
        To measure again now, <router-link to="/docs/hub/api#run-job">run <code>project-retention</code></router-link>
        — it applies retention and refreshes the figures in the same pass.
      </p>

      <h2 id="jobs">Jobs</h2>
      <p>
        <code>GET /api/v1/jobs</code> lists the scheduler's jobs. It is read-only: the settings come
        from <router-link to="/docs/hub/configuration#job-scheduler"><code>application.properties</code></router-link>
        and change only with a restart.
      </p>

      <DocsCodeBlock
        language="bash"
        :code="jobsRequest"
      />

      <DocsCodeBlock
        language="json"
        :code="jobsResponse"
      />

      <ul>
        <li><code>id</code> — the job's configuration key: its settings live under <code>jeffrey.hub.scheduler.jobs.&lt;id&gt;.*</code>.</li>
        <li><code>enabled</code> — whether the job runs on its schedule.</li>
        <li><code>period</code> — how often it runs, as an ISO-8601 duration (<code>PT5M</code> is five minutes).</li>
        <li><code>manualRun</code> — whether <code>POST /api/v1/jobs/{job}/run</code> accepts it.</li>
        <li><code>params</code> — the job's <code>.params.*</code> entries, as resolved at startup.</li>
      </ul>

      <h2 id="job">One Job</h2>
      <p>
        <code>GET /api/v1/jobs/{job}</code> returns a single entry of the list above. An unknown id
        answers <code>404</code> (see <router-link to="/docs/hub/api#errors">Errors</router-link>).
      </p>

      <DocsCodeBlock
        language="bash"
        :code="jobRequest"
      />

      <h2 id="run-job">Run a Job Now</h2>
      <p>
        <code>POST /api/v1/jobs/{job}/run</code> runs the job now, synchronously, and answers when it
        has finished. It takes the same per-job lock as the scheduled tick, so a tick that is still
        running is waited for, never run alongside. Only <code>project-retention</code> supports it
        (<code>manualRun: true</code>).
      </p>

      <DocsCodeBlock
        language="bash"
        :code="runRequest"
      />

      <DocsCodeBlock
        language="json"
        :code="runResponse"
      />

      <p>
        <code>startedAt</code> is when the run began, <code>durationMs</code> how long it took, and
        <code>summary</code> what it deleted and what is left. A job without manual run answers
        <code>409</code>; an unknown job <code>404</code>.
      </p>

      <h2 id="health">Health Probes</h2>
      <p>
        Outside <code>/api/v1</code>, Spring Boot's own liveness and readiness endpoints are what the
        <router-link to="/docs/hub/deployment/helm-chart">Helm chart</router-link>'s probes call:
      </p>

      <DocsCodeBlock
        language="bash"
        :code="healthRequest"
      />

      <h2 id="errors">Errors</h2>
      <p>
        An error answers with a JSON body carrying a <code>type</code>, a machine-readable
        <code>code</code> and a <code>message</code>.
      </p>
      <table>
        <thead>
          <tr>
            <th>Status</th>
            <th>Code</th>
            <th>When</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>404</code></td>
            <td><code>SCHEDULER_JOB_NOT_FOUND</code></td>
            <td><code>GET /api/v1/jobs/{job}</code> or <code>POST /api/v1/jobs/{job}/run</code> with an unknown job id</td>
          </tr>
          <tr>
            <td><code>409</code></td>
            <td><code>INVALID_REQUEST</code></td>
            <td><code>POST /api/v1/jobs/{job}/run</code> for a job that cannot be run manually</td>
          </tr>
        </tbody>
      </table>

      <DocsCodeBlock
        language="text"
        :code="notFoundResponse"
      />

      <DocsCodeBlock
        language="text"
        :code="conflictResponse"
      />

      <h2 id="examples">Examples</h2>
      <p>Biggest projects first, across every workspace:</p>

      <DocsCodeBlock
        language="bash"
        :code="biggestProjects"
      />

      <p>Apply retention now, then read the fresh figures:</p>

      <DocsCodeBlock
        language="bash"
        :code="measureNow"
      />

      <DocsCallout type="tip">
        The hub's earlier web UI — its Storage page at <code>/</code> and Scheduler page at
        <code>/scheduler</code> — and every <code>/api/internal/*</code> route are gone. Scripts that
        called <code>/api/internal/storage</code> or <code>/api/internal/scheduler/jobs</code> move to
        <code>/api/v1/storage</code> and <code>/api/v1/jobs</code>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
