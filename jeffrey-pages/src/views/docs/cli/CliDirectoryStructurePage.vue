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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'jeffrey-home-structure', text: 'Jeffrey Home Structure', level: 2 },
  { id: 'session-directory', text: 'Session Directory', level: 2 },
  { id: 'streaming-repository', text: 'Streaming Repository', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">CLI</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Directory Structure</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-folder-fill"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Directory Structure</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Jeffrey CLI creates a well-organized directory structure for storing profiling data. Understanding this structure helps with troubleshooting and managing disk space.</p>

        <h2 id="jeffrey-home-structure">Jeffrey Home Structure</h2>
        <p>The <code>jeffrey-home</code> directory (configured in your CLI config) contains all Jeffrey data organized by workspaces and projects:</p>

        <div class="directory-structure">
          <pre><code>&lt;jeffrey-home&gt;/
└── workspaces/
    └── &lt;workspace-id&gt;/
        ├── .settings/                       # Cached profiler settings from Jeffrey
        │   └── settings-2025-01-15T....json
        └── &lt;project-name&gt;/
            ├── .project-info.json           # Project metadata
            └── &lt;session-id&gt;/
                ├── .session-info.json       # Session metadata
                ├── profile-1704067200.jfr   # Async-Profiler output
                ├── profile-1704067800.jfr
                └── ...</code></pre>
        </div>

        <DocsCallout type="info">
          <strong>Workspace isolation:</strong> Each workspace has its own directory, making it easy to manage separate environments (production, staging, development).
        </DocsCallout>

        <h2 id="session-directory">Session Directory</h2>
        <p>Each recording session creates a directory with a unique identifier. The contents depend on your configuration, but Async-Profiler generates chunked JFR files using a timestamp pattern:</p>

        <div class="directory-structure">
          <pre><code>&lt;session-id&gt;/
├── profile-1704067200.jfr       # JFR chunk (timestamp-based naming)
├── profile-1704067800.jfr       # Next chunk after loop interval
├── profile-1704068400.jfr       # ... more chunks
├── heap-dump.hprof.gz           # Heap dump (if captured)
├── jfr-jvm.log                  # JVM log (if enabled)
├── perf-counters.hsperfdata     # Performance counters
└── .session-info.json           # Session metadata</code></pre>
        </div>

        <p>Async-Profiler creates new chunks based on the <code>loop</code> and <code>chunksize</code> parameters. The <code>%t</code> placeholder in the file pattern is replaced with the current timestamp.</p>

        <h2 id="streaming-repository">Streaming Repository</h2>
        <p>When <code>messaging</code> is enabled, an additional <code>streaming-repo/</code> subdirectory is created for JDK's JFR streaming repository:</p>

        <div class="directory-structure">
          <pre><code>&lt;session-id&gt;/
├── profile-*.jfr                # Async-Profiler output
├── streaming-repo/              # JDK JFR streaming repository
│   ├── repository/
│   │   ├── metadata             # Repository metadata
│   │   ├── chunk0               # Streaming chunks
│   │   ├── chunk1
│   │   └── ...
│   └── .mark                    # Repository marker
└── .session-info.json</code></pre>
        </div>

        <p>The streaming repository is used for <strong>ImportantMessage</strong> events that require immediate processing. Unlike Async-Profiler files which are written periodically, streaming events are captured in real-time and stored in the JDK's native repository format.</p>

        <DocsCallout type="info">
          <strong>Two recording mechanisms:</strong> Async-Profiler generates high-performance profiling data (CPU, allocation, lock) while the JDK streaming repository captures ImportantMessage events. Both coexist in the same session directory.
        </DocsCallout>
      </div>

      <nav class="docs-nav-footer">
        <router-link
          v-if="adjacentPages.prev"
          :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
          class="nav-link prev"
        >
          <i class="bi bi-arrow-left"></i>
          <div class="nav-text">
            <span class="nav-label">Previous</span>
            <span class="nav-title">{{ adjacentPages.prev.title }}</span>
          </div>
        </router-link>
        <div v-else class="nav-spacer"></div>
        <router-link
          v-if="adjacentPages.next"
          :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
          class="nav-link next"
        >
          <div class="nav-text">
            <span class="nav-label">Next</span>
            <span class="nav-title">{{ adjacentPages.next.title }}</span>
          </div>
          <i class="bi bi-arrow-right"></i>
        </router-link>
      </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Directory Structure */
.directory-structure {
  margin: 1rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.directory-structure pre {
  margin: 0;
  padding: 1rem;
  background: #1e293b;
  overflow-x: auto;
}

.directory-structure code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  color: #e2e8f0;
  line-height: 1.5;
}
</style>
