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
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'logging', text: 'Logging', level: 2 },
  { id: 'profile', text: 'Profile Initialization', level: 2 },
  { id: 'visualization', text: 'Visualization', level: 2 },
  { id: 'database', text: 'Database', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Advanced Properties"
      icon="bi bi-sliders"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        Tuning and specialised settings for Jeffrey Microscope. All Microscope-specific keys live under
        the <code>jeffrey.microscope.</code> namespace; standard Spring Boot keys are also accepted.
      </p>

      <DocsCallout type="info">
        <strong>All optional:</strong> these properties default to values that work for typical
        deployments. Override only what you need.
      </DocsCallout>

      <h2 id="logging">Logging</h2>
      <p>Standard Spring Boot logging keys apply. Microscope logs under the <code>cafe.jeffrey</code> package.</p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>logging.<wbr>level.<wbr>cafe.<wbr>jeffrey</code></td>
            <td><code>INFO</code></td>
            <td>Log level for all Microscope code. Set to <code>DEBUG</code> for verbose output.</td>
          </tr>
        </tbody>
      </table>

      <p>
        The connector's own loggers &mdash; the per-request log of the Azure file share and the Azure SDK's
        &mdash; are documented with the connector: see
        <router-link to="/docs/connectors/azure-files#logging">Connectors &rsaquo; Azure File Share &rsaquo; Logging</router-link>.
      </p>

      <h2 id="profile">Profile Initialization</h2>
      <p>Controls how recordings are parsed into per-profile DuckDB databases.</p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>profile.<wbr>frame-resolution</code></td>
            <td><code>CACHE</code></td>
            <td>
              Frame-resolution mode for flamegraphs. <code>CACHE</code> resolves frames in-memory
              (~10× faster); <code>DATABASE</code> resolves them in SQL (lower memory).
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>profile.<wbr>data-initializer.<wbr>enabled</code></td>
            <td><code>true</code></td>
            <td>Run the data initializer when a profile is created.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="visualization">Visualization</h2>
      <p>Flamegraph rendering thresholds.</p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>visualization.<wbr>flamegraph.<wbr>min-frame-threshold-pct</code></td>
            <td><code>0.05</code></td>
            <td>
              Minimum frame width (as a percentage of the root total) below which frames are collapsed in
              the flamegraph. Lower values show more detail at the cost of rendering performance.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>visualization.<wbr>flamegraph.<wbr>frame-text-mode</code></td>
            <td><code>single-line</code></td>
            <td>
              How frame labels are laid out within frames: <code>single-line</code> or
              <code>two-line</code>. This is the default every flamegraph starts with; each one still
              has its own toggle.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>ai-export.<wbr>flamegraph.<wbr>min-frame-threshold-pct</code></td>
            <td><code>2.0</code></td>
            <td>
              Prune threshold for the Markdown flamegraph export handed to a coding agent &mdash; the
              MCP flamegraph, compare and trace-span exports. Subtrees below
              this share of the samples are dropped; coarser than the visualization threshold to keep the
              export compact.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="database">Database</h2>
      <p>The Microscope core uses a single DuckDB file under <code>jeffrey.microscope.home.dir</code>; per-profile DuckDB files are created automatically.</p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.<wbr>microscope.<wbr>persistence.<wbr>database.<wbr>url</code></td>
            <td><code>jdbc:duckdb:<wbr>${jeffrey.<wbr>microscope.<wbr>home.<wbr>dir}<wbr>/jeffrey-data.db</code></td>
            <td>JDBC URL for the Microscope core DuckDB file. Override only if you want to relocate the file outside the home directory.</td>
          </tr>
        </tbody>
      </table>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Property names break after each dot. A share of the width, not a fixed minimum, keeps them
   to a few lines without the long descriptions squeezing them, and never widens the table. */
.docs-content td:first-child {
  width: 32%;
}
</style>
