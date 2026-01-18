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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-are-profiles', text: 'What are Profiles?', level: 2 },
  { id: 'profile-storage', text: 'Profile Storage', level: 2 },
  { id: 'profile-vs-recording', text: 'Profile vs Recording', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const folderStructure = `$JEFFREY_HOME/
└── profiles/
    ├── {profile-id-1}/
    │   ├── profile-data.db       # DuckDB database with all events
    │   └── heap-dump-analysis/   # Heap dump analysis data (if available)
    ├── {profile-id-2}/
    │   ├── profile-data.db
    │   └── heap-dump-analysis/
    └── ...`;
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Profiles Overview"
        icon="bi bi-speedometer2"
      />

      <div class="docs-content">
        <p>Profiles are the core analysis unit in Jeffrey - they represent <strong>processed and analyzed JFR recordings</strong> optimized for fast querying and interactive visualization.</p>

        <h2 id="what-are-profiles">What are Profiles?</h2>
        <p>A profile is created when Jeffrey processes a JFR recording. Unlike raw JFR files, profiles are stored in a database format that enables:</p>
        <ul>
          <li><strong>Fast querying</strong> - SQL-based access to all events</li>
          <li><strong>Pre-computed visualizations</strong> - Flamegraphs and charts ready instantly</li>
          <li><strong>Indexed data</strong> - Quick filtering and searching</li>
          <li><strong>Cached analysis</strong> - Complex computations stored for reuse</li>
        </ul>

        <p>Think of a profile as a "materialized view" of your JFR recording - all the data is there, but organized for analysis rather than storage.</p>

        <h2 id="profile-storage">Profile Storage</h2>
        <p>Every profile in Jeffrey has its own dedicated <strong>DuckDB database</strong>. This isolation provides several benefits:</p>
        <ul>
          <li><strong>Independent analysis</strong> - Each profile can be queried without affecting others</li>
          <li><strong>Easy cleanup</strong> - Deleting a profile removes its entire database</li>
          <li><strong>Portable profiles</strong> - Profile folders can be backed up or moved independently</li>
          <li><strong>Parallel processing</strong> - Multiple profiles can be analyzed simultaneously</li>
        </ul>

        <h3>Folder Structure</h3>
        <p>Profiles are stored in a hierarchical folder structure under <code>$JEFFREY_HOME</code>:</p>

        <DocsCodeBlock :code="folderStructure" language="text" />

        <p>Each profile folder contains:</p>
        <table>
          <thead>
            <tr>
              <th>File/Folder</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>profile-data.db</code></td>
              <td>The DuckDB database containing all parsed JFR events and metadata</td>
            </tr>
            <tr>
              <td><code>heap-dump-analysis/</code></td>
              <td>Heap dump analysis data when a heap dump is associated with the profile</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="info">
          <strong>Database per profile:</strong> Unlike traditional approaches that store all data in a single database, Jeffrey creates a separate DuckDB instance for each profile. This makes profiles self-contained and easy to manage.
        </DocsCallout>

        <h2 id="profile-vs-recording">Profile vs Recording</h2>
        <p>Understanding the difference between recordings and profiles is important:</p>

        <table>
          <thead>
            <tr>
              <th>Aspect</th>
              <th>Recording</th>
              <th>Profile</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Format</td>
              <td>Raw JFR file (.jfr)</td>
              <td>DuckDB database + caches</td>
            </tr>
            <tr>
              <td>Purpose</td>
              <td>Storage of original data</td>
              <td>Fast analysis and visualization</td>
            </tr>
            <tr>
              <td>Size</td>
              <td>Compact binary format</td>
              <td>Larger (indexed data + caches)</td>
            </tr>
            <tr>
              <td>Portability</td>
              <td>Standard JFR format</td>
              <td>Jeffrey-specific</td>
            </tr>
            <tr>
              <td>Creation</td>
              <td>JVM, async-profiler, agents</td>
              <td>Created in Jeffrey from recordings</td>
            </tr>
            <tr>
              <td>Queryable</td>
              <td>Requires parsing each time</td>
              <td>Instant SQL queries</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Storage strategy:</strong> Keep recordings as your source of truth. Profiles can be deleted and recreated anytime - they're just a processed view of the recording data.
        </DocsCallout>

        <DocsLinkCard
          to="/docs/concepts/projects/profiles"
          icon="bi bi-folder"
          title="Projects / Profiles"
          description="Learn about creating and managing profiles within projects"
        />
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
