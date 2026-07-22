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
      <DocsPageHeader
        title="Directory Structure"
        icon="bi bi-folder-fill"
      />

      <div class="docs-content">
        <p>Jeffrey Provisioner creates a well-organized directory structure for storing profiling data. Understanding this structure helps with troubleshooting and managing disk space.</p>

        <h2 id="jeffrey-home-structure">Jeffrey Home Structure</h2>
        <p>The <code>jeffrey-home</code> directory (configured in your provisioner config) contains all Jeffrey data organized by workspaces and projects:</p>

        <div class="directory-structure">
          <pre><code>&lt;jeffrey-home&gt;/
└── workspaces/
    └── &lt;workspace-id&gt;/
        ├── .settings/                       # Cached profiler settings from Jeffrey
        │   └── settings-2025-01-15T....json
        └── &lt;project-name&gt;/
            ├── .project-info.json           # Project metadata
            └── &lt;instance-name&gt;/
                ├── .instance-info.json      # Instance metadata
                └── &lt;session-id&gt;/
                    ├── .session-info.json   # Session metadata
                    ├── profile-1704067200.jfr # Async-Profiler output
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
├── streaming-repo/              # JDK JFR streaming repository (always created)
├── .heartbeat/                  # Agent liveness files
│   ├── heartbeat                # Epoch millis, rewritten every 5 seconds
│   └── finished                 # Clean-exit marker (written on JVM shutdown)
├── heap-dump.hprof.gz           # Heap dump (if captured)
├── jfr-jvm.log                  # JVM log (if enabled)
├── perf-counters.hsperfdata     # Performance counters (if enabled)
├── hs-jvm-err.log               # HotSpot error log (written on JVM crash)
└── .session-info.json           # Session metadata</code></pre>
        </div>

        <p>Async-Profiler creates new chunks based on the <code>loop</code> and <code>chunksize</code> parameters. The <code>%t</code> placeholder in the file pattern is replaced with the current timestamp.</p>

        <DocsCallout type="info">
          <strong>Liveness files:</strong> the Jeffrey Agent rewrites <code>.heartbeat/heartbeat</code> every 5 seconds and writes <code>.heartbeat/finished</code> from its shutdown hook on clean exit. The hub finishes a session immediately when the <code>finished</code> marker appears, and falls back to heartbeat staleness for crashed JVMs. The presence of <code>hs-jvm-err.log</code> indicates a JVM crash was detected.
        </DocsCallout>

        <h2 id="streaming-repository">Streaming Repository</h2>
        <p>The <code>streaming-repo/</code> subdirectory is created unconditionally and the JVM is started with <code>-XX:FlightRecorderOptions=repository=&lt;session&gt;/streaming-repo</code>, so JDK's JFR streaming repository lives inside the session directory:</p>

        <div class="directory-structure">
          <pre><code>&lt;session-id&gt;/
├── profile-*.jfr                # Async-Profiler output
├── streaming-repo/              # JDK JFR streaming repository
│   ├── metadata                 # Repository metadata
│   ├── chunk0                   # Streaming chunks
│   ├── chunk1
│   └── ...
└── .session-info.json</code></pre>
        </div>

        <p>The streaming repository lets the hub stream live JFR events from a running session — including the <code>jeffrey.AppInformation</code> event the Jeffrey Agent emits at the start of every JFR chunk, which makes each chunk self-describing (workspace, project, instance, session, order).</p>

        <DocsCallout type="info">
          <strong>Two recording mechanisms:</strong> Async-Profiler generates high-performance profiling data (CPU, allocation, lock) written as chunked <code>profile-*.jfr</code> files, while the JDK streaming repository captures live events for real-time streaming. Both coexist in the same session directory.
        </DocsCallout>
      </div>

      <DocsNavFooter />
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
