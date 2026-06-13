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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'throughput', text: 'Throughput', level: 2 },
  { id: 'files', text: 'Top Files', level: 2 },
  { id: 'directories', text: 'By Directory', level: 2 },
  { id: 'slowest', text: 'Slowest Operations', level: 2 },
  { id: 'events', text: 'Source Events', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader title="File I/O" icon="bi bi-file-earmark" />

    <div class="docs-content">
      <p>The File I/O page surfaces blocking file reads and writes. Each one blocks the calling thread until the OS satisfies it — instantly from the page cache, or slowly from disk on a cache miss or <code>fsync</code>. The page attributes that wait to the files and directories your application touches.</p>

      <DocsCallout type="tip">
        <strong>Reading it:</strong> a single file dominating bytes is the cue to cache it or batch writes; a hot <em>directory</em> spread across many rotating files (logs) only shows up in the By Directory view.
      </DocsCallout>

      <h2 id="overview">Overview</h2>
      <p>The header strip shows total bytes read and written, the file operation count, and the single slowest operation with its file.</p>

      <h2 id="throughput">Throughput</h2>
      <p>Bytes read and written per second over file descriptors. Heavy sustained writes often mean logging or flushing; repeated reads of the same files suggest a missing in-memory cache.</p>

      <h2 id="files">Top Files</h2>
      <p>Individual files ranked by bytes (with a share bar), operation count and total/max time, from <code>jdk.FileRead</code> / <code>jdk.FileWrite</code>.</p>

      <h2 id="directories">By Directory</h2>
      <p>The same file I/O rolled up per parent directory — surfaces a hot log directory or data directory even when the traffic is spread across many rotating files.</p>

      <h2 id="slowest">Slowest Operations</h2>
      <p>The slowest individual reads/writes by duration. A slow write is often an <code>fsync</code>/flush; a slow read is a page-cache miss hitting the disk.</p>

      <h2 id="events">Source Events</h2>
      <ul>
        <li><code>jdk.FileRead</code> — path, bytes read, end-of-stream and duration (threshold-gated, often disabled by default).</li>
        <li><code>jdk.FileWrite</code> — path, bytes written and duration (threshold-gated, often disabled by default).</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
