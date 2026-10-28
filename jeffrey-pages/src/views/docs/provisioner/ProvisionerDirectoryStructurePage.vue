<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'jeffrey-home-structure', text: 'Jeffrey Home Structure', level: 2 },
  { id: 'session-directory', text: 'Session Directory', level: 2 },
  { id: 'recorded-events', text: 'Recorded Events', level: 2 }
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
    └── &lt;workspace-id&gt;/                  # Workspace id = folder name (the reference id), created if missing
        └── &lt;project-name&gt;/                  # Project id = folder name (the configured project name)
            └── &lt;instance-name&gt;/             # Instance id = folder name (instance-name, HOSTNAME by default)
                └── &lt;session-id&gt;/            # Session id = folder name, a UUIDv7 (its timestamp is the start)
                    ├── rec-20261027-085500+0100.jfr # Async-Profiler output
                    ├── rec-20261027-091000+0100.jfr
                    └── ...</code></pre>
        </div>

        <p>The folder names are the state. A workspace's id is its folder (its reference id), a project's id is its folder (the project <code>name</code> the Provisioner was configured with), an instance's id is its folder, and a session's id is its folder &mdash; a UUIDv7 whose timestamp is the session's start. There are no marker files anywhere on the volume: a workspace is a visible root folder named with a valid reference id, which is also its name, and the Provisioner creates that folder when it is missing &mdash; a workspace does not have to be created before the Provisioner writes into it. The Provisioner decides whether a project or an instance is new by whether its folder exists. A project or instance is any visible (non-dot) child directory; a session is a visible directory named with a UUIDv7, and anything else under an instance is ignored. Its recordings show up in Microscope and on the Hub's Storage page without any create step.</p>

        <DocsCallout type="warning">
          <strong>Upgrading from a release with marker files:</strong> there is no compatibility with older volumes &mdash; re-provision. Links, downloads, MCP references and profiles that carried the old random project id are orphaned, since a project's id is now its folder name. Project rename is gone: the name in Settings is read-only. Old <code>.workspace-info.json</code>, <code>.project-info.json</code>, <code>.instance-info.json</code> and <code>.session-info.json</code> files left in folders are ignored as hidden entries and go with their directory. Workspace display names are gone: a workspace whose display name differed from its folder now shows the folder name, its reference id. Chunks an older Provisioner named <code>profile-…</code> are still listed and downloadable as recordings, but they are timed by the volume rather than by their name; new chunks are named <code>rec-…</code>.
        </DocsCallout>

        <DocsCallout type="info">
          <strong>Workspace isolation:</strong> Each workspace has its own directory, making it easy to manage separate environments (production, staging, development).
        </DocsCallout>

        <h2 id="session-directory">Session Directory</h2>
        <p>Each recording session creates a directory with a unique identifier. The contents depend on your configuration, but Async-Profiler generates chunked JFR files using a timestamp pattern:</p>

        <div class="directory-structure">
          <pre><code>&lt;session-id&gt;/
├── rec-20261027-085500+0100.jfr  # JFR chunk (local timestamp + UTC offset)
├── rec-20261027-091000+0100.jfr  # Next chunk after loop interval
├── rec-20261027-092500+0100.jfr  # ... more chunks
├── heap-dump.hprof.gz           # Heap dump (if captured)
├── jfr.jvm-log                  # JVM log (if enabled)
├── perf-counters.hsperfdata     # Performance counters (if enabled)
└── hs-jvm-err.log               # HotSpot error log (written on JVM crash)</code></pre>
        </div>

        <p>Async-Profiler creates new chunks based on the <code>loop</code> and <code>chunksize</code> parameters. The <code>%t</code> placeholder in the file pattern is replaced with the current timestamp, in the machine's local time zone, so the Provisioner writes the UTC offset of its own zone (<code>TZ</code> or <code>/etc/localtime</code>) at the session's start into the file template as literal text: <code>rec-%t&lt;offset&gt;.jfr</code>, the offset without its colon (<code>+0100</code>, <code>-0330</code>, and <code>+0000</code> on a UTC machine: <code>rec-20260220-120500+0000.jfr</code>). The Hub and Microscope read each chunk's timestamp at the offset its own name carries; a name without an offset is read as UTC. The offset is fixed for the session: in a zone with daylight saving, the chunks a JVM writes after a switch read one hour off until it restarts. UTC machines are never affected.</p>

        <DocsCallout type="info">
          <strong>Session order and crashes:</strong> nothing in the session reports liveness &mdash; the Hub and Microscope order sessions by their UUIDv7 directory names and read the file names. A <code>.heartbeat/</code> folder left by an older release is ignored and goes with its session. The presence of <code>hs-jvm-err.log</code> indicates a JVM crash was detected.
        </DocsCallout>

        <h2 id="recorded-events">Recorded Events</h2>
        <p>Async-Profiler writes CPU, allocation, and lock samples to <code>rec-*.jfr</code> files. Its <code>jfrsync</code> setting captures JVM and Jeffrey instrumentation events in those recordings. Microscope pulls the dumped files off the volume one at a time, and the chunks it downloads stay as they were written — the several files one recording is made of, never joined into a single one.</p>

        <DocsCallout type="info">
          <strong>Where the JVM keeps its own JFR chunks:</strong> <code>jfrsync</code> runs a JDK recording alongside Async-Profiler, and the JVM buffers that recording in its own JFR repository. Jeffrey no longer points that repository at the session directory, so it goes wherever the JVM puts it by default &mdash; a timestamped folder under <code>java.io.tmpdir</code>. Nothing Jeffrey reads lives there, and the working set is transient, but it is disk the session volume used to absorb. On a container with a small <code>/tmp</code> or a tight ephemeral-storage limit, set <code>-XX:FlightRecorderOptions=repository=&lt;path&gt;</code> yourself to put it somewhere sized for it.
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
