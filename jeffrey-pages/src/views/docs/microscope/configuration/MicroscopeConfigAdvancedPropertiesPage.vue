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
  { id: 'database', text: 'Database', level: 2 },
  { id: 'guardian', text: 'Guardian Thresholds', level: 2 },
  { id: 'seed-recordings', text: 'Seed Recordings', level: 2 }
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
            <td><code>logging.level.cafe.jeffrey</code></td>
            <td><code>INFO</code></td>
            <td>Log level for all Microscope code. Set to <code>DEBUG</code> for verbose output.</td>
          </tr>
        </tbody>
      </table>

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
            <td><code>jeffrey.microscope.profile.frame-resolution</code></td>
            <td><code>CACHE</code></td>
            <td>
              Frame-resolution mode for flamegraphs. <code>CACHE</code> resolves frames in-memory
              (~10× faster); <code>DATABASE</code> resolves them in SQL (lower memory).
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.profile.data-initializer.enabled</code></td>
            <td><code>true</code></td>
            <td>Run the data initializer when a profile is created.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.profile.data-initializer.blocking</code></td>
            <td><code>true</code></td>
            <td>Block the request until initialization completes (vs. async).</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.profile.data-initializer.concurrent</code></td>
            <td><code>true</code></td>
            <td>Allow per-table initializers to run in parallel.</td>
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
            <td><code>jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct</code></td>
            <td><code>0.05</code></td>
            <td>
              Minimum frame width (as a fraction of the parent) below which frames are collapsed in
              the flamegraph. Lower values show more detail at the cost of rendering performance.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.visualization.flamegraph.frame-text-mode</code></td>
            <td><code>single-line</code></td>
            <td>How frame labels are laid out within frames. Currently <code>single-line</code>.</td>
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
            <td><code>jeffrey.microscope.persistence.database.url</code></td>
            <td><code>jdbc:duckdb:${jeffrey.microscope.home.dir}/jeffrey-data.db</code></td>
            <td>JDBC URL for the Microscope core DuckDB file. Override only if you want to relocate the file outside the home directory.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="guardian">Guardian Thresholds</h2>
      <p>
        The Guardian engine ships ~60 tunable thresholds under
        <code>jeffrey.microscope.guardian.*</code> — minimum sample counts per group, INFO/WARNING
        severity bands per check (logging frameworks, allocations, blocking, GC, JIT, safepoints,
        virtual-thread pinning, …). Defaults are tuned empirically; only override them when a specific
        check is too noisy or too quiet for your workload.
      </p>
      <p>
        See <code>GuardianProperties.java</code> in the <code>profile-guardian</code> module for the
        full list of keys, defaults, and what each threshold controls.
      </p>

      <h2 id="seed-recordings">Seed Recordings</h2>
      <p>
        Pre-loads a directory of JFR recordings into the Microscope on first start —
        used by the example <code>tour-with-examples</code> container image.
      </p>

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
            <td><code>jeffrey.microscope.seed.recordings.enabled</code></td>
            <td><code>false</code></td>
            <td>Enable seeding from a directory at startup.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.seed.recordings.dir</code></td>
            <td><code>/jeffrey-examples</code></td>
            <td>Directory containing the JFR recordings to seed.</td>
          </tr>
        </tbody>
      </table>

    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
