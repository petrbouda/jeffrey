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

const storageRequest = `curl -s http://volume-scheduler:8080/api/v1/storage`;

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

const jobsRequest = `curl -s http://volume-scheduler:8080/api/v1/jobs`;

const jobsResponse = `[
  {
    "id": "retention",
    "enabled": true,
    "period": "PT5M",
    "settings": {
      "sessionRetention": "PT168H",
      "maxSessions": 10,
      "newestSessionFileRetention": "PT168H",
      "maxSizeBytes": 21474836480,
      "instanceRetention": "PT336H"
    }
  },
  {
    "id": "compression",
    "enabled": true,
    "period": "PT1M",
    "settings": null
  }
]`;

const jobRequest = `curl -s http://volume-scheduler:8080/api/v1/jobs/retention`;

const runRequest = `curl -s -X POST http://volume-scheduler:8080/api/v1/jobs/retention/run`;

const runResponse = `{
  "job": "retention",
  "startedAt": "2026-11-08T20:37:14.278Z",
  "took": "PT0.153S",
  "summary": "Deleted 1 session, 0 files and 0 instances; 1.2 GB across 3 projects"
}`;

const compressionRunResponse = `{
  "job": "compression",
  "startedAt": "2026-11-08T20:38:02.104Z",
  "took": "PT1.87S",
  "summary": "Compressed 4 recordings in 2 sessions across 3 projects"
}`;

const healthRequest = `curl -s http://volume-scheduler:8080/actuator/health/liveness
curl -s http://volume-scheduler:8080/actuator/health/readiness`;

const notFoundResponse = `HTTP/1.1 404 Not Found

{
  "code": "JOB_NOT_FOUND",
  "message": "No such job: cleanup"
}`;

const methodNotAllowedResponse = `HTTP/1.1 405 Method Not Allowed

{
  "code": "METHOD_NOT_ALLOWED",
  "message": "Request method 'GET' is not supported"
}`;

const biggestProjects = `curl -s http://volume-scheduler:8080/api/v1/storage \\
  | jq -r '.workspaces[] | .id as $ws | .projects[] | "\\(.totalBytes)\\t\\($ws)/\\(.id)"' \\
  | sort -rn`;

const measureNow = `curl -s -X POST http://volume-scheduler:8080/api/v1/jobs/retention/run | jq -r .summary
curl -s http://volume-scheduler:8080/api/v1/storage | jq .measuredAt`;

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
        The Volume Scheduler has no web UI. It serves a small JSON REST API on its HTTP port
        (<code>8080</code> by default, <code>server.port</code>) for operators and scripts: what every
        project occupies on the shared volume, its two jobs, and a way to run either one now.
        Microscope never calls it — it reads the volume itself.
      </p>

      <h2 id="overview">Overview</h2>
      <p>Every endpoint lives under <code>http://&lt;volume-scheduler&gt;:8080/api/v1</code> and speaks JSON only.</p>
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
            <td>Both jobs, their schedule and settings, read-only</td>
          </tr>
          <tr>
            <td><code>GET</code></td>
            <td><code>/api/v1/jobs/{job}</code></td>
            <td>One job: <code>retention</code> or <code>compression</code></td>
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
        <code>POST /api/v1/jobs/retention/run</code> deletes data exactly like a scheduled retention pass
        would. Keep the volume scheduler's Service inside the cluster; if you put an Ingress in front of
        it, protect it there.
      </DocsCallout>

      <h2 id="storage">Storage</h2>
      <p>
        <code>GET /api/v1/storage</code> returns what every project occupies on the volume, as
        measured by the last <code>retention</code> pass — totals for the whole volume, per
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
        <li><code>measuredAt</code> — when the pass that produced these figures ran; <code>null</code> until the first pass.</li>
        <li><code>totalBytes</code>, <code>totalFiles</code> — what is left on the volume after that pass.</li>
        <li><code>lastActivityAt</code> — the project's most recent activity, or <code>null</code> when it has none.</li>
      </ul>

      <DocsCallout type="info">
        <strong>The figures can be up to one retention period old</strong> — 5 minutes by default
        (<code>jeffrey.volume-scheduler.retention.period</code>). The first pass runs at startup;
        until it finishes — or, with retention disabled, until it is run by hand — the endpoint answers
        with no figures:
      </DocsCallout>

      <DocsCodeBlock
        language="json"
        :code="storageBeforeFirstPass"
      />

      <p>
        To measure again now, <a href="#run-job">run <code>retention</code></a> — it applies retention
        and refreshes the figures in the same pass.
      </p>

      <h2 id="jobs">Jobs</h2>
      <p>
        <code>GET /api/v1/jobs</code> lists both jobs. It is read-only: the settings come from
        <router-link to="/docs/volume-scheduler/configuration"><code>jeffrey.volume-scheduler.*</code></router-link>
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
        <li><code>id</code> — <code>retention</code> or <code>compression</code>, the job's path segment and its configuration group: <code>jeffrey.volume-scheduler.&lt;id&gt;.*</code>.</li>
        <li><code>enabled</code> — whether the job runs on its schedule. A disabled job is still listed, and still runs by hand.</li>
        <li><code>period</code> — the fixed delay between runs, as an ISO-8601 duration (<code>PT5M</code> is five minutes).</li>
        <li><code>settings</code> — for <code>retention</code>, the policy it applies, as bound at startup: the durations in ISO-8601 (<code>PT168H</code> is seven days), <code>maxSessions</code> per instance, and the project budget <code>maxSizeBytes</code> in bytes. <code>null</code> for <code>compression</code>, which has no settings beyond its schedule.</li>
      </ul>

      <h2 id="job">One Job</h2>
      <p>
        <code>GET /api/v1/jobs/{job}</code> returns a single entry of the list above. An unknown id
        answers <code>404</code> (see <a href="#errors">Errors</a>).
      </p>

      <DocsCodeBlock
        language="bash"
        :code="jobRequest"
      />

      <h2 id="run-job">Run a Job Now</h2>
      <p>
        <code>POST /api/v1/jobs/{job}/run</code> runs the job now, synchronously, and answers when it
        has finished. It works for both jobs, enabled or not, and takes the same per-job lock as the
        scheduled run, so a scheduled run that is still in flight is waited for, never run alongside.
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
        <code>startedAt</code> is when the run began, <code>took</code> how long it took as an ISO-8601
        duration, and <code>summary</code> the one line the job reports — for retention, what it deleted
        and what is left; for compression, what it compressed:
      </p>

      <DocsCodeBlock
        language="json"
        :code="compressionRunResponse"
      />

      <p>An unknown job answers <code>404</code>.</p>

      <h2 id="health">Health Probes</h2>
      <p>
        Outside <code>/api/v1</code>, Spring Boot's own liveness and readiness endpoints are what the
        <router-link to="/docs/deployment/helm-chart">Helm chart</router-link>'s probes call. They are
        enabled on every start, inside Kubernetes or not, so a plain <code>docker run</code> answers them
        too:
      </p>

      <DocsCodeBlock
        language="bash"
        :code="healthRequest"
      />

      <h2 id="errors">Errors</h2>
      <p>
        An error answers with a JSON body carrying a machine-readable <code>code</code> and a
        <code>message</code>.
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
            <td><code>JOB_NOT_FOUND</code></td>
            <td><code>GET /api/v1/jobs/{job}</code> or <code>POST /api/v1/jobs/{job}/run</code> with a job id other than <code>retention</code> or <code>compression</code></td>
          </tr>
          <tr>
            <td><code>404</code></td>
            <td><code>NOT_FOUND</code></td>
            <td>A path the API does not serve</td>
          </tr>
          <tr>
            <td><code>405</code></td>
            <td><code>METHOD_NOT_ALLOWED</code></td>
            <td>A method the path does not take, e.g. <code>GET /api/v1/jobs/{job}/run</code></td>
          </tr>
          <tr>
            <td><code>4xx</code></td>
            <td><code>BAD_REQUEST</code></td>
            <td>Any other request Spring refuses, with its own status — e.g. <code>415</code> for an unsupported content type</td>
          </tr>
          <tr>
            <td><code>500</code></td>
            <td><code>INTERNAL_ERROR</code></td>
            <td>Anything else that failed; the cause is in the log</td>
          </tr>
        </tbody>
      </table>

      <DocsCodeBlock
        language="text"
        :code="notFoundResponse"
      />

      <DocsCodeBlock
        language="text"
        :code="methodNotAllowedResponse"
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
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
