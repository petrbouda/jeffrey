<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
  { id: 'what-are-recordings', text: 'What is Recordings?', level: 2 },
  { id: 'workflow', text: 'Workflow', level: 2 },
  { id: 'recording-groups', text: 'Recording Groups', level: 2 },
  { id: 'recordings-vs-projects', text: 'Recordings vs Projects', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Recordings"
        icon="bi bi-record-circle"
      />

      <div class="docs-content">
        <p><strong>Recordings</strong> is the single home for every recording you investigate in Jeffrey — whether you uploaded it yourself or it was auto-downloaded from a live project session. Every analysis starts here.</p>

        <h2 id="what-are-recordings">What is Recordings?</h2>
        <p>Open the <strong>Recordings</strong> entry in the top navigation. The page lists every recording on the local machine — manual uploads alongside artifacts pulled from project sessions — and is the launch point for creating profiles.</p>
        <ul>
          <li><strong>Single inbox</strong> — there's no separate per-project recordings tab; everything lands here.</li>
          <li><strong>Origin breadcrumb</strong> — recordings that came from a project session display a <code>server › workspace › project</code> chip on the card so you can trace them back to their source.</li>
          <li><strong>Group organization</strong> — recordings can be organized into flat, user-named groups.</li>
          <li><strong>Full analysis features</strong> — profiles created here have access to flamegraphs, timeseries, thread analysis, GC analysis, heap-dump analysis, and everything else.</li>
        </ul>

        <DocsCallout type="info">
          <strong>Where do recordings come from?</strong> Manual upload directly on this page <em>or</em> the <strong>Download</strong> button on a recording session inside a project's Instances view. Both paths persist the file to local storage; the download path additionally writes <code>origin.*</code> tags so the breadcrumb can be rendered.
        </DocsCallout>

        <h2 id="workflow">Workflow</h2>
        <p>Two ways to bring a recording in, then a single path to analysis:</p>

        <div class="workflow-steps">
          <div class="workflow-step">
            <div class="step-number">1a</div>
            <div class="step-content">
              <h4>Manual upload</h4>
              <p>Drop a JFR file or heap dump on the upload panel at the top of the Recordings page.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">1b</div>
            <div class="step-content">
              <h4>Auto-download from a project session</h4>
              <p>From a project's <strong>Instances</strong> view, open a session and click <strong>Download</strong>. The merged recording (plus heap dumps and logs) is streamed to local storage and shows up on the Recordings page tagged with its origin.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <h4>Analyze</h4>
              <p>Click <strong>Analyze</strong> on a recording to create a profile. Jeffrey parses the file and builds the profile database.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <h4>Investigate</h4>
              <p>Open the profile to access flamegraphs, timeseries, thread analysis, and all other profile features.</p>
            </div>
          </div>
        </div>

        <h2 id="recording-groups">Recording Groups</h2>
        <p>Recordings can be organized into <strong>groups</strong>:</p>
        <ul>
          <li><strong>Create groups</strong> — add named groups to categorize recordings (e.g. by investigation topic, incident, or date).</li>
          <li><strong>Move recordings</strong> — drag-and-drop or use the move action; a recording belongs to at most one group.</li>
          <li><strong>Delete groups</strong> — removes the group and its recordings.</li>
        </ul>
        <p>Groups are flat (one level) and orthogonal to the origin breadcrumb. A "Load tests" group can mix manually-uploaded files and auto-downloaded recordings from any project — the breadcrumb still tells you where each came from.</p>

        <h2 id="recordings-vs-projects">Recordings vs Projects</h2>
        <p>Projects organize <strong>live applications</strong> — their instances, sessions, profiler settings, and event streams. Recordings is the post-capture artifact view: once a session has produced something worth keeping, you bring it here for analysis.</p>
        <table>
          <thead>
            <tr>
              <th>You're looking for…</th>
              <th>Go to…</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Live event stream from a running JVM</td>
              <td>Project → Instances → session → Live Stream</td>
            </tr>
            <tr>
              <td>List of past recording sessions in a project</td>
              <td>Project → Instances → session</td>
            </tr>
            <tr>
              <td>Analyzing a captured JFR / heap dump</td>
              <td><strong>Recordings</strong> (top nav)</td>
            </tr>
            <tr>
              <td>Profile (post-analysis)</td>
              <td>Click any analyzed recording on the Recordings page</td>
            </tr>
          </tbody>
        </table>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Workflow Steps */
.workflow-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.workflow-step {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.step-number {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.85rem;
}

.step-content {
  flex: 1;
}

.step-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.step-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}
</style>
