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
  { id: 'profiles-in-projects', text: 'Profiles in Projects', level: 2 },
  { id: 'where-profiles-come-from', text: 'Where Profiles Come From', level: 2 },
  { id: 'profile-creation-process', text: 'Profile Creation Process', level: 2 },
  { id: 'profile-initialization', text: 'Profile Initialization', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Profiles"
        icon="bi bi-file-earmark-text"
      />

      <div class="docs-content">
        <p>Profiles are <strong>analyzed recordings</strong> ready for investigation. Profiles are created and managed within the <strong>Recordings</strong> section of a project.</p>

        <DocsCallout type="info">
          For comprehensive information about what profiles contain and the analysis features they provide, see <router-link to="/docs/local/profiles">Profiles</router-link>.
        </DocsCallout>

        <h2 id="profiles-in-projects">Profiles in Projects</h2>
        <p>Each project maintains its own list of profiles. Profiles are always associated with a specific project and cannot be shared between projects. This separation ensures:</p>
        <ul>
          <li>Clear organization of analysis work</li>
          <li>Profiles stay close to their source recordings</li>
          <li>Project-level comparisons are straightforward</li>
        </ul>

        <h2 id="where-profiles-come-from">Where Profiles Come From</h2>
        <p>Profiles are created from recordings within the same project. Recordings can come from multiple sources:</p>

        <ul>
          <li><strong>Manual uploads</strong> - Upload a JFR file directly to the Recordings section</li>
          <li><strong>Repository</strong> - Recording sessions from live applications connected via a jeffrey-server</li>
          <li><strong>Quick Analysis</strong> - Upload and analyze a JFR file without creating a workspace or project</li>
        </ul>

        <p>From Repository: Browse sessions, use Merge and Copy, the recording appears in Recordings, then create a profile from it.</p>

        <h2 id="profile-creation-process">Profile Creation Process</h2>
        <p>To create a profile from a recording:</p>

        <div class="process-steps">
          <div class="process-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <strong>Navigate to Recordings</strong>
              <p>Go to your project's Recordings section and find the JFR file you want to analyze.</p>
            </div>
          </div>
          <div class="process-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <strong>Click "Create Profile"</strong>
              <p>Select the recording and click the Create Profile button to start initialization.</p>
            </div>
          </div>
          <div class="process-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <strong>Wait for Initialization</strong>
              <p>Jeffrey parses the JFR file, extracts events, and builds the profile database.</p>
            </div>
          </div>
          <div class="process-step">
            <div class="step-number">4</div>
            <div class="step-content">
              <strong>Profile Ready</strong>
              <p>The profile appears in the Profiles section, ready for analysis.</p>
            </div>
          </div>
        </div>

        <h2 id="profile-initialization">Profile Initialization</h2>
        <p>Profile initialization is the process of converting a raw JFR recording into an analyzable profile. Understanding this process helps set expectations:</p>

        <h3>What Happens During Initialization</h3>
        <ol>
          <li><strong>JFR Parsing</strong> - Jeffrey reads and decodes all events from the JFR file</li>
          <li><strong>Event Storage</strong> - Events are stored in a DuckDB database with proper indexing</li>
          <li><strong>Cache Generation</strong> - Pre-computed data structures are built for quicker access:
            <ul>
              <li>Thread statistics and timeline data</li>
              <li>Guardian analysis results</li>
            </ul>
          </li>
          <li><strong>Metadata Extraction</strong> - JVM info, event types, and time ranges are recorded</li>
        </ol>

        <h3>Initialization Time</h3>
        <p>Initialization time depends on several factors:</p>

        <table>
          <thead>
            <tr>
              <th>Factor</th>
              <th>Impact</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Recording size</td>
              <td>Larger files take longer to parse</td>
            </tr>
            <tr>
              <td>Event count</td>
              <td>More events = more processing</td>
            </tr>
            <tr>
              <td>Event types</td>
              <td>Stack-based events (CPU, allocation) require more processing</td>
            </tr>
            <tr>
              <td>System resources</td>
              <td>CPU and memory affect speed</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="warning">
          <strong>Resource Usage:</strong> Profile initialization is CPU and memory intensive. This is why using Remote workspaces to analyze recordings from the server is recommended - initialization runs on your local machine instead of the server.
        </DocsCallout>

        <h3>Profile Status</h3>
        <p>During and after initialization, profiles have a status:</p>
        <ul>
          <li><strong>Initializing</strong> - Profile is being created (progress shown)</li>
          <li><strong>Ready</strong> - Profile is complete and available for analysis</li>
          <li><strong>Failed</strong> - Initialization encountered an error</li>
        </ul>

      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Process Steps */
.process-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.process-step {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
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
  font-weight: 600;
  font-size: 0.9rem;
}

.step-content {
  flex: 1;
  padding-top: 0.25rem;
}

.step-content strong {
  display: block;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.step-content p {
  margin: 0;
  font-size: 0.9rem;
  color: #5e6e82;
}

/* Nested list spacing */
ol > li > ul {
  margin-top: 0.5rem;
}
</style>
