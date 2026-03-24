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
  { id: 'what-is-quick-analysis', text: 'What is Quick Analysis?', level: 2 },
  { id: 'workflow', text: 'Workflow', level: 2 },
  { id: 'recording-groups', text: 'Recording Groups', level: 2 },
  { id: 'quick-analysis-vs-projects', text: 'Quick Analysis vs Projects', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Quick Analysis"
        icon="bi bi-lightning"
      />

      <div class="docs-content">
        <p>Quick Analysis provides the <strong>fastest path from a JFR file to analysis</strong>. Upload a recording and start investigating immediately without creating a workspace or project first.</p>

        <h2 id="what-is-quick-analysis">What is Quick Analysis?</h2>
        <p>Quick Analysis is a global feature accessible from the home page. It's designed for scenarios where you want to quickly look at a JFR recording without the overhead of organizing it into a workspace and project:</p>
        <ul>
          <li><strong>Immediate analysis</strong> - Upload a JFR file and create a profile in one step</li>
          <li><strong>No setup required</strong> - No need to create workspaces or projects</li>
          <li><strong>Group organization</strong> - Optionally organize recordings into named groups</li>
          <li><strong>Full analysis features</strong> - Profiles created via Quick Analysis have all the same analysis capabilities as regular profiles</li>
        </ul>

        <DocsCallout type="tip">
          <strong>When to use Quick Analysis:</strong> Use it when you have a JFR file and want to start analyzing immediately without setting up a workspace or project.
        </DocsCallout>

        <h2 id="workflow">Workflow</h2>
        <p>Quick Analysis follows a streamlined workflow:</p>

        <div class="workflow-steps">
          <div class="workflow-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <h4>Upload</h4>
              <p>Upload a JFR file from your machine. The recording appears in the Quick Analysis list.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <h4>Analyze</h4>
              <p>Click <strong>Analyze</strong> on a recording to create a profile. Jeffrey parses the JFR file and builds the profile database.</p>
            </div>
          </div>
          <div class="workflow-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <h4>Investigate</h4>
              <p>Once the profile is ready, click to open it. You get full access to flamegraphs, timeseries, thread analysis, and all other profile features.</p>
            </div>
          </div>
        </div>

        <h2 id="recording-groups">Recording Groups</h2>
        <p>As you accumulate recordings in Quick Analysis, you can organize them into <strong>groups</strong>:</p>
        <ul>
          <li><strong>Create groups</strong> - Add named groups to categorize recordings (e.g., by investigation topic or date)</li>
          <li><strong>Move recordings</strong> - Move recordings between groups to keep things organized</li>
          <li><strong>Delete groups</strong> - Remove groups when no longer needed</li>
        </ul>

        <h2 id="quick-analysis-vs-projects">Quick Analysis vs Projects</h2>
        <table>
          <thead>
            <tr>
              <th>Aspect</th>
              <th>Quick Analysis</th>
              <th>Project Recordings</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Setup</td>
              <td>None - upload and analyze</td>
              <td>Requires workspace and project</td>
            </tr>
            <tr>
              <td>Organization</td>
              <td>Flat list with optional groups</td>
              <td>Hierarchical (workspace / project)</td>
            </tr>
            <tr>
              <td>Best For</td>
              <td>Quick one-off investigations</td>
              <td>Long-term organized analysis</td>
            </tr>
            <tr>
              <td>Analysis Features</td>
              <td>Full (identical)</td>
              <td>Full (identical)</td>
            </tr>
            <tr>
              <td>Differential Analysis</td>
              <td>Yes</td>
              <td>Yes</td>
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
